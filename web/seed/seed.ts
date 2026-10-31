import {
	movies as demoMovies,
	people as demoPeople,
	type SeedMovie,
	type SeedPerson
} from './films.ts';
import { readFileSync } from 'node:fs';

export interface Artwork {
	bytes: Buffer;
	fileName: string;
	contentType: string;
}

export interface Api {
	request<T>(query: string, variables?: Record<string, unknown>): Promise<T>;
	upload(movieId: string, version: number, artwork: Artwork): Promise<void>;
}

export class SeedError extends Error {
	name = 'SeedError';
	readonly code?: string;

	// No parameter property: Node runs this file by stripping types, which doesn't support them.
	constructor(message: string, code?: string) {
		super(message);
		this.code = code;
	}
}

/** Where a movie's artwork comes from. */
export type PosterSource = (movie: SeedMovie) => Promise<Artwork>;

const SHARED_POSTER: Artwork = {
	bytes: readFileSync(new URL('./poster.png', import.meta.url)),
	fileName: 'poster.png',
	contentType: 'image/png'
};

export const sharedPoster: PosterSource = async () => SHARED_POSTER;

/** The film's real poster from TMDB, fetched with the user's own API read access token. */
export function tmdbPosters(
	token: string,
	doFetch: typeof fetch = fetch,
	timeoutMs = 30_000
): PosterSource {
	async function get(url: string, headers: Record<string, string> = {}): Promise<Response> {
		const response = await doFetch(url, { headers, signal: AbortSignal.timeout(timeoutMs) });
		if (!response.ok) throw new Error(`TMDB answered HTTP ${response.status}`);
		return response;
	}

	return async (movie) => {
		const details = await get(`https://api.themoviedb.org/3/movie/${movie.tmdbId}`, {
			Authorization: `Bearer ${token}`
		});
		const { poster_path: path } = (await details.json()) as { poster_path?: string | null };
		if (!path) throw new Error('TMDB has no poster for it');
		const image = await get(`https://image.tmdb.org/t/p/w780${path}`);
		return {
			bytes: Buffer.from(await image.arrayBuffer()),
			fileName: `${movie.tmdbId}.jpg`,
			contentType: image.headers.get('content-type') ?? 'image/jpeg'
		};
	};
}

export interface SeedData {
	people?: SeedPerson[];
	movies?: SeedMovie[];
	posters?: PosterSource;
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

/** Adds the demo data through the API. Only runs on an empty catalog, since names are not unique. */
export async function seedCatalog(
	api: Api,
	log: (line: string) => void,
	{ people = demoPeople, movies = demoMovies, posters = sharedPoster }: SeedData = {}
): Promise<void> {
	// Check before sending anything, so bad data never leaves a partial catalog.
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

	// Nothing is undone on failure: a timed-out request may still have been saved.
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

		// The list shows newest first, so add them in reverse.
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
			const artwork = movie.poster ? await addArtwork(api, movie, createMovie, posters) : '';

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
			log(`Added ${movie.title}${artwork} and ${credits.length} credits.`);
		}
	} catch (error) {
		const reason = (error instanceof Error ? error.message : String(error)).replace(/\.$/, '');
		// A failed request may still have been saved.
		const state = added.people > 0 ? 'is now' : 'may now be';
		throw new SeedError(
			`Seeding stopped after adding ${added.people} people and ${added.movies} movies: ${reason}. ` +
				`The catalog ${state} partly seeded, so running the seed again may refuse; ` +
				'docker compose down -v empties it.'
		);
	}
}

/**
 * Uploads the movie's artwork and says where it came from. A poster that can't be fetched, or
 * that the server refuses, is replaced by the shared one. Other upload errors stop the seed,
 * since the upload may still have been saved.
 */
async function addArtwork(
	api: Api,
	movie: SeedMovie,
	created: { id: string; version: number },
	posters: PosterSource
): Promise<string> {
	if (posters === sharedPoster) {
		await api.upload(created.id, created.version, SHARED_POSTER);
		return ' with artwork';
	}
	try {
		await api.upload(created.id, created.version, await posters(movie));
		return ' with its TMDB poster';
	} catch (error) {
		if (error instanceof SeedError && error.code !== 'BAD_REQUEST') throw error;
		await api.upload(created.id, created.version, SHARED_POSTER);
		const reason = (error as Error).message.replace(/\.$/, '');
		return ` with the shared poster (no TMDB poster: ${reason})`;
	}
}

/** Each request times out, so a server that never answers cannot hang the seed. */
export function graphQlApi(url: string, doFetch: typeof fetch = fetch, timeoutMs = 30_000): Api {
	async function send<T>(init: RequestInit): Promise<T> {
		let response: Response;
		let text: string;
		try {
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
			throw new SeedError(
				`${error.extensions?.code ?? 'ERROR'}: ${error.message ?? 'no message'}`,
				error.extensions?.code
			);
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
		async upload(movieId, version, artwork) {
			const form = new FormData();
			form.append(
				'operations',
				JSON.stringify({ query: UPLOAD_ARTWORK, variables: { movieId, version, file: null } })
			);
			form.append('map', JSON.stringify({ '0': ['variables.file'] }));
			form.append(
				'0',
				new Blob([new Uint8Array(artwork.bytes)], { type: artwork.contentType }),
				artwork.fileName
			);
			await send({ body: form });
		}
	};
}
