import {
	movies as demoMovies,
	people as demoPeople,
	type SeedMovie,
	type SeedPerson
} from './films.ts';
import { posterPng } from './poster.ts';

/** What the seed needs from the GraphQL API, so tests can stand in for the server. */
export interface Api {
	request<T>(query: string, variables?: Record<string, unknown>): Promise<T>;
	/** Uploads a PNG as the movie's artwork. */
	upload(movieId: string, version: number, png: Buffer, fileName: string): Promise<void>;
}

/** A failure the person running the seed can act on; run.ts prints only its message. */
export class SeedError extends Error {
	name = 'SeedError';
}

const COUNTS = `query SeedCounts {
	movies(page: { limit: 1 }) { totalCount }
	people(page: { limit: 1 }) { totalCount }
}`;
const CREATE_PERSON = `mutation SeedPerson($input: PersonInput!) { createPerson(input: $input) { id } }`;
const CREATE_MOVIE = `mutation SeedMovie($input: MovieInput!) { createMovie(input: $input) { id version } }`;
const ADD_CREDIT = `mutation SeedCredit($input: AddCreditInput!) { addCredit(input: $input) { id } }`;
const UPLOAD_ARTWORK = `mutation SeedArtwork($movieId: ID!, $version: Int!, $file: Upload!) {
	uploadArtwork(movieId: $movieId, version: $version, file: $file) { version }
}`;

/**
 * Adds the demo people, movies, artwork and credits through the public API, so every rule the
 * app enforces applies to them too. It only runs on an empty catalog: seeding twice would add
 * every movie and person again, since titles and names are not unique.
 */
export async function seedCatalog(
	api: Api,
	log: (line: string) => void,
	people: SeedPerson[] = demoPeople,
	movies: SeedMovie[] = demoMovies
): Promise<void> {
	// Checked before anything is sent, so a mistake in the data never leaves a partial catalog.
	const listed = new Set(people.map((p) => p.name));
	for (const movie of movies) {
		for (const { name } of [...movie.cast, ...movie.creators]) {
			if (!listed.has(name)) {
				throw new SeedError(`${movie.title} credits ${name}, who is not in the list of people.`);
			}
		}
	}

	const counts = await api.request<{
		movies: { totalCount: number };
		people: { totalCount: number };
	}>(COUNTS);
	if (counts.movies.totalCount > 0 || counts.people.totalCount > 0) {
		throw new SeedError(
			`The catalog already has ${counts.movies.totalCount} movies and ${counts.people.totalCount} people, so nothing was added. Seed an empty catalog only (docker compose down -v deletes all data).`
		);
	}

	// Nothing is undone after a failure: a timed-out request may have been saved anyway, so the
	// message says how far it got and how to start again instead.
	const added = { people: 0, movies: 0 };
	try {
		const personIds = new Map<string, string>();
		for (const person of people) {
			const { createPerson } = await api.request<{ createPerson: { id: string } }>(CREATE_PERSON, {
				input: { name: person.name, bio: person.bio ?? null, birthDate: person.birthDate ?? null }
			});
			personIds.set(person.name, createPerson.id);
			added.people++;
		}
		log(`Added ${people.length} people.`);

		// The movie list shows the newest first, so adding them last to first lists them as written.
		for (const movie of [...movies].reverse()) {
			const { createMovie } = await api.request<{ createMovie: { id: string; version: number } }>(
				CREATE_MOVIE,
				{
					input: {
						title: movie.title,
						synopsis: movie.synopsis,
						releaseDate: movie.releaseDate,
						runtimeMinutes: movie.runtimeMinutes,
						genres: movie.genres
					}
				}
			);
			if (movie.poster) {
				await api.upload(createMovie.id, createMovie.version, posterPng(movie.title), 'poster.png');
			}

			const credits = [
				...movie.cast.map((c, i) => ({
					name: c.name,
					type: 'CAST',
					characterName: c.character,
					billingOrder: i
				})),
				...movie.creators.map((c, i) => ({
					name: c.name,
					type: c.role,
					characterName: null,
					billingOrder: i
				}))
			];
			for (const { name, ...credit } of credits) {
				const personId = personIds.get(name)!;
				await api.request(ADD_CREDIT, { input: { movieId: createMovie.id, personId, ...credit } });
			}
			added.movies++;
			log(
				`Added ${movie.title}${movie.poster ? ' with artwork' : ''} and ${credits.length} credits.`
			);
		}
	} catch (error) {
		// The server's messages end with a full stop; this one adds its own.
		const reason = (error instanceof Error ? error.message : String(error)).replace(/\.$/, '');
		// With nothing confirmed, the failed request may still have been saved.
		const state = added.people > 0 ? 'is now' : 'may now be';
		throw new SeedError(
			`Seeding stopped after adding ${added.people} people and ${added.movies} movies: ${reason}. ` +
				`The catalog ${state} partly seeded, so running the seed again may refuse; ` +
				'docker compose down -v empties it.'
		);
	}
}

/**
 * The real API at `url`: JSON requests, and GraphQL multipart requests for artwork. Each request
 * gives up after `timeoutMs`, so a server that accepts a connection but never answers cannot
 * hang the seed; 30 seconds leaves room for an upload, which the gateway allows 60.
 */
export function graphQlApi(url: string, doFetch: typeof fetch = fetch, timeoutMs = 30_000): Api {
	async function send<T>(init: RequestInit): Promise<T> {
		let response: Response;
		let text: string;
		try {
			// One signal for the whole exchange, so a body that stops arriving times out too.
			response = await doFetch(url, {
				method: 'POST',
				...init,
				signal: AbortSignal.timeout(timeoutMs)
			});
			text = await response.text();
		} catch (error) {
			if ((error as Error).name === 'TimeoutError') {
				throw new SeedError(`${url} did not answer within ${timeoutMs / 1000} seconds.`);
			}
			throw new SeedError(
				`Could not reach ${url} (${(error as Error).message}). Is the stack running?`
			);
		}
		let body: { data?: T | null; errors?: { message?: string; extensions?: { code?: string } }[] };
		try {
			body = JSON.parse(text);
		} catch {
			throw new SeedError(
				`${url} answered HTTP ${response.status} with a body that is not GraphQL.`
			);
		}
		const error = body.errors?.[0];
		if (error) {
			throw new SeedError(`${error.extensions?.code ?? 'ERROR'}: ${error.message ?? 'no message'}`);
		}
		if (body.data == null)
			throw new SeedError(`${url} answered HTTP ${response.status} with no data.`);
		return body.data;
	}

	return {
		request: (query, variables) =>
			send({
				headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
				body: JSON.stringify({ query, variables })
			}),
		async upload(movieId, version, png, fileName) {
			// The GraphQL multipart request spec: operations, a map from file part to variable, the file.
			const form = new FormData();
			form.append(
				'operations',
				JSON.stringify({ query: UPLOAD_ARTWORK, variables: { movieId, version, file: null } })
			);
			form.append('map', JSON.stringify({ '0': ['variables.file'] }));
			form.append('0', new Blob([new Uint8Array(png)], { type: 'image/png' }), fileName);
			await send({ body: form });
		}
	};
}
