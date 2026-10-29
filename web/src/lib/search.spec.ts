import { describe, expect, it } from 'vitest';
import { roleSummary, searchQueryString } from './search';

describe('roleSummary', () => {
	it('lists distinct roles in the usual order, then counts each movie once', () => {
		const credits = [
			{ type: 'DIRECTOR' as const, movie: { id: 'arrival' } },
			{ type: 'CAST' as const, movie: { id: 'arrival' } },
			{ type: 'CAST' as const, movie: { id: 'dune' } }
		];

		expect(roleSummary(credits)).toBe('Cast · Director, 2 movies');
	});

	it('says "1 movie" for one, and nothing for someone with no credits', () => {
		expect(roleSummary([{ type: 'WRITER', movie: { id: 'arrival' } }])).toBe('Writer, 1 movie');
		expect(roleSummary([])).toBeNull();
	});

	it('does not count a credit whose movie was deleted mid-request', () => {
		expect(
			roleSummary([
				{ type: 'PRODUCER', movie: null },
				{ type: 'PRODUCER', movie: { id: 'dune' } }
			])
		).toBe('Producer, 1 movie');
	});
});

describe('searchQueryString', () => {
	it('trims the text and encodes it, so any characters survive the address bar', () => {
		expect(searchQueryString('  100% & more ')).toBe('?q=100%25%20%26%20more');
		expect(searchQueryString('Zoë')).toBe('?q=Zo%C3%AB');
	});

	it('is empty for a blank query', () => {
		expect(searchQueryString('   ')).toBe('');
	});
});
