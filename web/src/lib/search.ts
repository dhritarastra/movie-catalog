import { CREDIT_TYPES, roleLabel } from './credits.ts';
import type { CreditType } from './graphql/generated/graphql.ts';

/** How many results the search page asks for: well under the catalog's catalog.search.max-limit. */
export const SEARCH_LIMIT = 10;

/** The search page's address for a query; a blank query is the page with no search. */
export function searchQueryString(text: string): string {
	const trimmed = text.trim();
	return trimmed ? `?q=${encodeURIComponent(trimmed)}` : '';
}

/**
 * What a person does, for a search result: their distinct roles in the usual order and how
 * many movies they are credited on, such as "Cast · Director, 2 movies". Null with no credits.
 */
export function roleSummary(
	credits: { type: CreditType; movie: { id: string } | null }[]
): string | null {
	const held = new Set(credits.map((credit) => credit.type));
	if (held.size === 0) return null;
	const roles = CREDIT_TYPES.filter((type) => held.has(type))
		.map(roleLabel)
		.join(' · ');
	// A credit whose movie was deleted mid-request has no movie, and is not counted.
	const movies = new Set(credits.flatMap((credit) => (credit.movie ? [credit.movie.id] : []))).size;
	return `${roles}, ${movies} ${movies === 1 ? 'movie' : 'movies'}`;
}
