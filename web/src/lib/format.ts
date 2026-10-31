export function yearOf(isoDate: string | null | undefined): string | null {
	return isoDate ? isoDate.slice(0, 4) : null;
}

const longDate = new Intl.DateTimeFormat('en-GB', {
	day: 'numeric',
	month: 'long',
	year: 'numeric',
	timeZone: 'UTC'
});

/** "1974-08-20" -> "20 August 1974", in UTC so the reader's time zone can't shift the day. */
export function formatDate(isoDate: string | null | undefined): string | null {
	return isoDate ? longDate.format(new Date(`${isoDate}T00:00:00Z`)) : null;
}

export function localToday(now: Date = new Date()): string {
	const pad = (n: number) => String(n).padStart(2, '0');
	return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function formatRuntime(minutes: number | null | undefined): string | null {
	if (minutes == null) return null;
	const hours = Math.floor(minutes / 60);
	const rest = minutes % 60;
	if (hours === 0) return `${rest}m`;
	return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
}

export function parseGenres(text: string): string[] {
	return text
		.split(',')
		.map((g) => g.trim())
		.filter((g) => g.length > 0);
}
