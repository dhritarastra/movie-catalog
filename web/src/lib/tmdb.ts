/** "Arrival (2016)", or just the title when TMDB has no release year. */
export function tmdbLabel(poster: { title: string; releaseYear: string | null }): string {
	return poster.releaseYear ? `${poster.title} (${poster.releaseYear})` : poster.title;
}
