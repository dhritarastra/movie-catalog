import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import type { SeedMovie, SeedPerson } from './films.ts';
import { graphQlApi, seedCatalog, SeedError, type Api } from './seed.ts';

interface Call {
	operation: string;
	variables?: Record<string, unknown>;
}

/** Stands in for the server: records each call and answers with made-up IDs and versions. */
function fakeApi(counts = { movies: 0, people: 0 }) {
	const calls: Call[] = [];
	let next = 0;
	const api: Api = {
		async request<T>(query: string, variables?: Record<string, unknown>) {
			const operation = /(?:query|mutation) (\w+)/.exec(query)![1];
			calls.push({ operation, variables });
			// Built on demand so each call takes exactly one new ID.
			const answers: Record<string, () => unknown> = {
				SeedCounts: () => ({
					movies: { totalCount: counts.movies },
					people: { totalCount: counts.people }
				}),
				SeedPerson: () => ({ createPerson: { id: `person-${++next}` } }),
				SeedMovie: () => ({ createMovie: { id: `movie-${++next}`, version: 7 } }),
				SeedCredit: () => ({ addCredit: { id: `credit-${++next}` } })
			};
			return answers[operation]() as T;
		},
		async upload(movieId, version, png, fileName) {
			calls.push({
				operation: 'upload',
				variables: { movieId, version, bytes: png.length, fileName }
			});
		}
	};
	return { api, calls };
}

const people: SeedPerson[] = [
	{ name: 'Ada Director', bio: 'Directs.', birthDate: '1970-01-02' },
	{ name: 'Bo Actor' }
];
const movie: SeedMovie = {
	title: 'Test Film',
	synopsis: 'A test.',
	releaseDate: '2020-01-01',
	runtimeMinutes: 90,
	genres: ['Drama'],
	poster: true,
	cast: [
		{ name: 'Bo Actor', character: 'Lead' },
		{ name: 'Ada Director', character: 'Cameo' }
	],
	creators: [
		{ name: 'Ada Director', role: 'DIRECTOR' },
		{ name: 'Ada Director', role: 'WRITER' }
	]
};

describe('seedCatalog', () => {
	it.each([[{ movies: 1, people: 0 }], [{ movies: 0, people: 3 }]])(
		'refuses a catalog that is not empty and adds nothing: %o',
		async (counts) => {
			const { api, calls } = fakeApi(counts);

			await expect(seedCatalog(api, () => {}, people, [movie])).rejects.toThrow(SeedError);
			expect(calls.map((c) => c.operation)).toEqual(['SeedCounts']);
		}
	);

	it('adds people, then each movie with its artwork and credits, using the IDs the API gave', async () => {
		const { api, calls } = fakeApi();
		const lines: string[] = [];

		await seedCatalog(api, (line) => lines.push(line), people, [movie]);

		expect(calls.map((c) => c.operation)).toEqual([
			'SeedCounts',
			'SeedPerson',
			'SeedPerson',
			'SeedMovie',
			'upload',
			'SeedCredit',
			'SeedCredit',
			'SeedCredit',
			'SeedCredit'
		]);
		// Missing optional fields are sent as null rather than left out.
		expect(calls[2].variables).toEqual({ input: { name: 'Bo Actor', bio: null, birthDate: null } });
		// The upload carries the version createMovie returned, or it would be a conflict.
		expect(calls[4].variables).toMatchObject({
			movieId: 'movie-3',
			version: 7,
			fileName: 'poster.png',
			bytes: readFileSync(new URL('./poster.png', import.meta.url)).length
		});
		expect(calls.slice(5).map((c) => c.variables?.input)).toEqual([
			{
				movieId: 'movie-3',
				personId: 'person-2',
				type: 'CAST',
				characterName: 'Lead',
				billingOrder: 0
			},
			{
				movieId: 'movie-3',
				personId: 'person-1',
				type: 'CAST',
				characterName: 'Cameo',
				billingOrder: 1
			},
			{
				movieId: 'movie-3',
				personId: 'person-1',
				type: 'DIRECTOR',
				characterName: null,
				billingOrder: 0
			},
			{
				movieId: 'movie-3',
				personId: 'person-1',
				type: 'WRITER',
				characterName: null,
				billingOrder: 1
			}
		]);
		expect(lines).toEqual(['Added 2 people.', 'Added Test Film with artwork and 4 credits.']);
	});

	it('adds the movies last to first, so the newest-first list shows them in the order given', async () => {
		const { api, calls } = fakeApi();
		const titles = ['First', 'Second', 'Third'];

		await seedCatalog(
			api,
			() => {},
			people,
			titles.map((title) => ({ ...movie, title }))
		);

		const created = calls.filter((c) => c.operation === 'SeedMovie');
		expect(created.map((c) => (c.variables?.input as { title: string }).title)).toEqual([
			'Third',
			'Second',
			'First'
		]);
	});

	it('uploads nothing for a movie without a poster', async () => {
		const { api, calls } = fakeApi();

		await seedCatalog(api, () => {}, people, [{ ...movie, poster: false }]);

		expect(calls.map((c) => c.operation)).not.toContain('upload');
	});

	it('refuses a credit for someone who is not in the list of people before sending anything', async () => {
		const { api, calls } = fakeApi();
		const stranger = { ...movie, cast: [{ name: 'Nobody Listed', character: 'X' }] };

		await expect(seedCatalog(api, () => {}, people, [stranger])).rejects.toThrow(
			'Test Film credits Nobody Listed, who is not in the list of people.'
		);
		expect(calls).toEqual([]);
	});

	it('says what was already added when a request fails part way, and how to start again', async () => {
		const { api } = fakeApi();
		let movieCalls = 0;
		const failing: Api = {
			...api,
			async request<T>(query: string, variables?: Record<string, unknown>) {
				if (query.includes('SeedMovie') && ++movieCalls === 2) {
					throw new SeedError('SERVICE_UNAVAILABLE: catalog timed out.');
				}
				return api.request<T>(query, variables);
			}
		};
		const titles = ['First', 'Second', 'Third'];

		const run = seedCatalog(
			failing,
			() => {},
			people,
			titles.map((title) => ({ ...movie, title }))
		);

		await expect(run).rejects.toThrow(
			'Seeding stopped after adding 2 people and 1 movies: SERVICE_UNAVAILABLE: catalog timed out. ' +
				'The catalog is now partly seeded, so running the seed again may refuse; ' +
				'docker compose down -v empties it.'
		);
	});

	it('says the catalog may be partly seeded when even the first person was not confirmed', async () => {
		const { api } = fakeApi();
		const failing: Api = {
			...api,
			async request<T>(query: string, variables?: Record<string, unknown>) {
				if (query.includes('SeedPerson'))
					throw new SeedError('http://x did not answer within 30 seconds.');
				return api.request<T>(query, variables);
			}
		};

		await expect(seedCatalog(failing, () => {}, people, [movie])).rejects.toThrow(
			'Seeding stopped after adding 0 people and 0 movies: http://x did not answer within 30 seconds. ' +
				'The catalog may now be partly seeded'
		);
	});
});

describe('poster.png', () => {
	it('is a PNG within the 5 MiB upload limit', () => {
		const png = readFileSync(new URL('./poster.png', import.meta.url));

		expect(png.subarray(0, 8)).toEqual(
			Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
		);
		expect(png.length).toBeLessThan(5 * 1024 * 1024);
	});
});

describe('graphQlApi', () => {
	const url = 'http://example.test/graphql';
	const answering = (status: number, body: string) =>
		(async () => new Response(body, { status })) as unknown as typeof fetch;

	it('returns the data of a successful request', async () => {
		const api = graphQlApi(url, answering(200, '{"data":{"ok":true}}'));

		await expect(api.request('query Ok { ok }')).resolves.toEqual({ ok: true });
	});

	it('reports a GraphQL error with its code and message', async () => {
		const api = graphQlApi(
			url,
			answering(
				200,
				'{"errors":[{"message":"title: is required","extensions":{"code":"BAD_REQUEST"}}]}'
			)
		);

		await expect(api.request('query X { x }')).rejects.toThrow('BAD_REQUEST: title: is required');
	});

	it('explains an unreachable server and a response that is not GraphQL', async () => {
		const unreachable = (async () => {
			throw new TypeError('fetch failed');
		}) as unknown as typeof fetch;

		await expect(graphQlApi(url, unreachable).request('query X { x }')).rejects.toThrow(
			`Could not reach ${url} (fetch failed). Is the stack running?`
		);
		await expect(
			graphQlApi(url, answering(502, '<html>Bad Gateway</html>')).request('query X { x }')
		).rejects.toThrow('answered HTTP 502 with a body that is not GraphQL');
	});

	it('reports a response with neither data nor errors', async () => {
		const api = graphQlApi(url, answering(200, '{"data":null}'));

		await expect(api.request('query X { x }')).rejects.toThrow(
			`${url} answered HTTP 200 with no data.`
		);
	});

	it('reports the GraphQL error of an error status that has a GraphQL body', async () => {
		const api = graphQlApi(
			url,
			answering(
				503,
				'{"errors":[{"message":"catalog timed out","extensions":{"code":"SERVICE_UNAVAILABLE"}}]}'
			)
		);

		await expect(api.request('query X { x }')).rejects.toThrow(
			'SERVICE_UNAVAILABLE: catalog timed out'
		);
	});

	it('reports an upload the server refused', async () => {
		const api = graphQlApi(
			url,
			answering(200, '{"errors":[{"message":"not an image","extensions":{"code":"BAD_REQUEST"}}]}')
		);

		await expect(api.upload('movie-1', 1, Buffer.from([1]), 'poster.png')).rejects.toThrow(
			'BAD_REQUEST: not an image'
		);
	});

	it('gives up on a server that never answers', async () => {
		// Accepts the request and never answers, until the request is aborted.
		const silent = ((_url: string, init: RequestInit) =>
			new Promise((_resolve, reject) =>
				init.signal!.addEventListener('abort', () => reject(init.signal!.reason))
			)) as unknown as typeof fetch;

		await expect(graphQlApi(url, silent, 20).request('query X { x }')).rejects.toThrow(
			`${url} did not answer within 0.02 seconds.`
		);
	});

	it('uploads artwork as a GraphQL multipart request with a PNG file part', async () => {
		let sent: RequestInit | undefined;
		const capture = (async (_url: string, init: RequestInit) => {
			sent = init;
			return new Response('{"data":{"uploadArtwork":{"version":2}}}');
		}) as unknown as typeof fetch;

		await graphQlApi(url, capture).upload('movie-1', 1, Buffer.from([1, 2, 3]), 'poster.png');

		const form = sent!.body as FormData;
		expect(JSON.parse(form.get('operations') as string).variables).toEqual({
			movieId: 'movie-1',
			version: 1,
			file: null
		});
		expect(JSON.parse(form.get('map') as string)).toEqual({ '0': ['variables.file'] });
		const file = form.get('0') as File;
		expect([file.name, file.type, file.size]).toEqual(['poster.png', 'image/png', 3]);
	});
});
