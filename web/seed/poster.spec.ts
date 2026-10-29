import { inflateSync, crc32 } from 'node:zlib';
import { describe, expect, it } from 'vitest';
import { POSTER_HEIGHT, POSTER_WIDTH, posterPng } from './poster.ts';

/** Splits a PNG into its chunks, checking each one's CRC as a decoder would. */
function chunks(png: Buffer): { type: string; data: Buffer }[] {
	const found = [];
	let offset = 8;
	while (offset < png.length) {
		const length = png.readUInt32BE(offset);
		const typeAndData = png.subarray(offset + 4, offset + 8 + length);
		expect(png.readUInt32BE(offset + 8 + length)).toBe(crc32(typeAndData));
		found.push({
			type: typeAndData.subarray(0, 4).toString('ascii'),
			data: typeAndData.subarray(4)
		});
		offset += 12 + length;
	}
	return found;
}

describe('posterPng', () => {
	it('is a well-formed RGB PNG of the poster size', () => {
		const png = posterPng('Arrival');

		expect(png.subarray(0, 8)).toEqual(
			Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
		);
		const parts = chunks(png);
		expect(parts.map((c) => c.type)).toEqual(['IHDR', 'IDAT', 'IEND']);
		const header = parts[0].data;
		expect(header.readUInt32BE(0)).toBe(POSTER_WIDTH);
		expect(header.readUInt32BE(4)).toBe(POSTER_HEIGHT);
		expect([...header.subarray(8)]).toEqual([8, 2, 0, 0, 0]);

		// One filter byte and three bytes per pixel on every row, each row unfiltered.
		const pixels = inflateSync(parts[1].data);
		const rowBytes = 1 + POSTER_WIDTH * 3;
		expect(pixels.length).toBe(rowBytes * POSTER_HEIGHT);
		for (let y = 0; y < POSTER_HEIGHT; y++) expect(pixels[y * rowBytes]).toBe(0);
	});

	it('draws the same poster for the same title and a different one for another', () => {
		expect(posterPng('Dune').equals(posterPng('Dune'))).toBe(true);
		expect(posterPng('Dune').equals(posterPng('Parasite'))).toBe(false);
	});

	it('handles non-ASCII titles and stays far below the 5 MiB upload limit', () => {
		const png = posterPng('Amélie 🎬');

		expect(chunks(png).map((c) => c.type)).toEqual(['IHDR', 'IDAT', 'IEND']);
		expect(png.length).toBeLessThan(5 * 1024 * 1024);
	});
});
