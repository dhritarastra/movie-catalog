import { describe, expect, it } from 'vitest';
import { formatDate, formatRuntime, localToday, parseGenres, yearOf } from './format';

describe('format', () => {
	it('takes the year from an ISO date and tolerates a missing one', () => {
		expect(yearOf('2016-11-11')).toBe('2016');
		expect(yearOf(null)).toBeNull();
		expect(yearOf('')).toBeNull();
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

	it('writes calendar dates out in full, on the same day in every time zone', () => {
		expect(formatDate('1974-08-20')).toBe('20 August 1974');
		expect(formatDate('1800-01-01')).toBe('1 January 1800');
		expect(formatDate(null)).toBeNull();
	});

	it("gives today's local date as yyyy-MM-dd, padded", () => {
		expect(localToday(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05');
		expect(localToday(new Date(2026, 10, 28, 0, 1))).toBe('2026-11-28');
	});
});
