import { describe, expect, it } from 'vitest';
import { movies, people } from './films.ts';

// The seed goes through the API, which would refuse bad data halfway through and leave a
// partly seeded catalog. These checks catch that before any request is sent.

/** A real calendar date in yyyy-MM-dd form, such as the API accepts. */
function isRealDate(text: string): boolean {
	if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) return false;
	return new Date(`${text}T00:00:00Z`).toISOString().startsWith(text);
}

// The API's limits, from the Bean Validation annotations on MovieInput, PersonInput and
// CreditInput in catalog-service (and the CHECK constraints they mirror).
const TITLE_MAX = 200;
const NAME_MAX = 200;
const CHARACTER_MAX = 200;
const TEXT_MAX = 2000; // synopsis and bio
const GENRES_MAX = 10;
const GENRE_MAX = 50;
const RUNTIME_MAX = 1000;

const isTrimmedText = (text: string, max: number) =>
	text.trim() === text && text.length > 0 && text.length <= max;

describe('the demo people', () => {
	it('have names the API accepts, unique so credits can name them', () => {
		for (const person of people)
			expect(isTrimmedText(person.name, NAME_MAX), person.name).toBe(true);
		expect(new Set(people.map((p) => p.name)).size).toBe(people.length);
	});

	it('have bios within the limit and real birth dates that are not in the future', () => {
		const today = new Date().toISOString().slice(0, 10);
		for (const { name, bio, birthDate } of people) {
			if (bio !== undefined) expect(isTrimmedText(bio, TEXT_MAX), name).toBe(true);
			if (birthDate !== undefined) {
				expect(isRealDate(birthDate), name).toBe(true);
				expect(birthDate >= '1800-01-01' && birthDate <= today, name).toBe(true);
			}
		}
	});

	it('are each credited on at least one movie', () => {
		const credited = new Set(movies.flatMap((m) => [...m.cast, ...m.creators].map((c) => c.name)));
		expect(people.map((p) => p.name).filter((name) => !credited.has(name))).toEqual([]);
	});
});

describe('the demo movies', () => {
	it('have fields the API accepts', () => {
		for (const movie of movies) {
			expect(isTrimmedText(movie.title, TITLE_MAX), movie.title).toBe(true);
			expect(isTrimmedText(movie.synopsis, TEXT_MAX), movie.title).toBe(true);
			expect(isRealDate(movie.releaseDate), movie.title).toBe(true);
			expect(movie.releaseDate >= '1870-01-01' && movie.releaseDate <= '2100-12-31').toBe(true);
			expect(Number.isInteger(movie.runtimeMinutes)).toBe(true);
			expect(movie.runtimeMinutes).toBeGreaterThanOrEqual(1);
			expect(movie.runtimeMinutes).toBeLessThanOrEqual(RUNTIME_MAX);
			expect(movie.genres.length).toBeLessThanOrEqual(GENRES_MAX);
			for (const genre of movie.genres)
				expect(isTrimmedText(genre, GENRE_MAX), movie.title).toBe(true);
			// The API drops repeated genres ignoring case, so a repeat would be lost silently.
			expect(new Set(movie.genres.map((g) => g.toLowerCase())).size).toBe(movie.genres.length);
		}
	});

	it('credit only listed people, never the same person in the same role twice', () => {
		const names = new Set(people.map((p) => p.name));
		for (const movie of movies) {
			const roles = [
				...movie.cast.map((c) => `${c.name}|CAST|${c.character}`),
				...movie.creators.map((c) => `${c.name}|${c.role}|`)
			];
			expect(new Set(roles).size, movie.title).toBe(roles.length);
			for (const credit of [...movie.cast, ...movie.creators]) {
				expect(names.has(credit.name), `${movie.title}: ${credit.name}`).toBe(true);
			}
			for (const { character } of movie.cast) {
				expect(isTrimmedText(character, CHARACTER_MAX), movie.title).toBe(true);
			}
		}
	});
});
