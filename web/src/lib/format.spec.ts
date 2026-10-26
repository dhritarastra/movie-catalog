import { describe, expect, it } from 'vitest';
import { formatRuntime, parseGenres, releaseYear } from './format';

describe('format', () => {
	it('takes the year from an ISO date and tolerates a missing one', () => {
		expect(releaseYear('2016-11-11')).toBe('2016');
		expect(releaseYear(null)).toBeNull();
		expect(releaseYear('')).toBeNull();
	});

	it('formats runtimes as hours and minutes', () => {
		expect(formatRuntime(116)).toBe('1h 56m');
		expect(formatRuntime(120)).toBe('2h');
		expect(formatRuntime(45)).toBe('45m');
		expect(formatRuntime(null)).toBeNull();
	});

	it('splits comma-separated genres, trimming and dropping blanks', () => {
		expect(parseGenres(' Sci-Fi, Drama ,, ')).toEqual(['Sci-Fi', 'Drama']);
		expect(parseGenres('')).toEqual([]);
	});
});
