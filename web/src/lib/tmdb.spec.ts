import { describe, expect, it } from 'vitest';
import { tmdbLabel } from './tmdb';

describe('tmdbLabel', () => {
	it('names the film with its year, or alone when the year is unknown', () => {
		expect(tmdbLabel({ title: 'Arrival', releaseYear: '2016' })).toBe('Arrival (2016)');
		expect(tmdbLabel({ title: 'Home Video', releaseYear: null })).toBe('Home Video');
	});
});
