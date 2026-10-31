import { CREDIT_TYPES, roleLabel } from './credits.ts';
import type { CreditType } from './graphql/generated/graphql.ts';

export const SEARCH_LIMIT = 10;

export function searchQueryString(text: string): string {
	const trimmed = text.trim();
	return trimmed ? `?q=${encodeURIComponent(trimmed)}` : '';
}

/** e.g. "Cast · Director, 2 movies"; null with no credits. */
export function roleSummary(
	credits: { type: CreditType; movie: { id: string } | null }[]
): string | null {
	const held = new Set(credits.map((credit) => credit.type));
	if (held.size === 0) return null;
	const roles = CREDIT_TYPES.filter((type) => held.has(type))
		.map(roleLabel)
		.join(' · ');
	const movies = new Set(credits.flatMap((credit) => (credit.movie ? [credit.movie.id] : []))).size;
	return `${roles}, ${movies} ${movies === 1 ? 'movie' : 'movies'}`;
}
