/** "2016-11-11" -> "2016". */
export function yearOf(isoDate: string | null | undefined): string | null {
	return isoDate ? isoDate.slice(0, 4) : null;
}

const longDate = new Intl.DateTimeFormat('en-GB', {
	day: 'numeric',
	month: 'long',
	year: 'numeric',
	timeZone: 'UTC'
});

/**
 * "1974-08-20" -> "20 August 1974". The date is read and formatted in UTC: it is a calendar
 * date, not an instant, so the reader's time zone must not move it to the previous day.
 */
export function formatDate(isoDate: string | null | undefined): string | null {
	return isoDate ? longDate.format(new Date(`${isoDate}T00:00:00Z`)) : null;
}

/** Today in the reader's own time zone, as yyyy-MM-dd, for a date input's `max`. */
export function localToday(now: Date = new Date()): string {
	const pad = (n: number) => String(n).padStart(2, '0');
	return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
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
