/** "2016-11-11" -> "2016". */
export function releaseYear(releaseDate: string | null | undefined): string | null {
	return releaseDate ? releaseDate.slice(0, 4) : null;
}

/** 116 -> "1h 56m", 45 -> "45m". */
export function formatRuntime(minutes: number | null | undefined): string | null {
	if (minutes == null) return null;
	const hours = Math.floor(minutes / 60);
	const rest = minutes % 60;
	if (hours === 0) return `${rest}m`;
	return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
}

/** "Sci-Fi, Drama ,, drama" -> ["Sci-Fi", "Drama", "drama"]; the catalog de-duplicates. */
export function parseGenres(text: string): string[] {
	return text
		.split(',')
		.map((g) => g.trim())
		.filter((g) => g.length > 0);
}
