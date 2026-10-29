import { crc32, deflateSync } from 'node:zlib';

export const POSTER_WIDTH = 600;
export const POSTER_HEIGHT = 900;

/**
 * An original abstract poster for a title, as PNG bytes: a two-colour gradient with a disc,
 * the colours and the disc's place chosen from the title so each movie looks different and a
 * rerun draws the same picture. No text is drawn; the page shows the title beside it. Written
 * with only Node's zlib so the seed needs no packages and no image files are committed.
 */
export function posterPng(title: string): Buffer {
	const hash = fnv1a(title);
	const hue = hash % 360;
	const top = hsl(hue, 0.55, 0.5);
	const bottom = hsl((hue + 40) % 360, 0.6, 0.15);
	const disc = hsl((hue + 180) % 360, 0.5, 0.75);
	const discX = POSTER_WIDTH * (0.3 + ((hash >>> 9) % 40) / 100);
	const discY = POSTER_HEIGHT * 0.35;
	const discRadius = POSTER_WIDTH * 0.22;

	// Each row starts with PNG filter type 0 (none), then three bytes per pixel.
	const rowBytes = 1 + POSTER_WIDTH * 3;
	const pixels = Buffer.alloc(rowBytes * POSTER_HEIGHT);
	for (let y = 0; y < POSTER_HEIGHT; y++) {
		const t = y / (POSTER_HEIGHT - 1);
		for (let x = 0; x < POSTER_WIDTH; x++) {
			const inDisc = (x - discX) ** 2 + (y - discY) ** 2 <= discRadius ** 2;
			const offset = y * rowBytes + 1 + x * 3;
			for (let c = 0; c < 3; c++) {
				pixels[offset + c] = inDisc ? disc[c] : Math.round(top[c] + (bottom[c] - top[c]) * t);
			}
		}
	}

	const header = Buffer.alloc(13);
	header.writeUInt32BE(POSTER_WIDTH, 0);
	header.writeUInt32BE(POSTER_HEIGHT, 4);
	header[8] = 8; // bits per channel
	header[9] = 2; // colour type: RGB
	// Bytes 10 to 12 stay 0: deflate compression, standard filtering, no interlacing.

	return Buffer.concat([
		Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
		chunk('IHDR', header),
		chunk('IDAT', deflateSync(pixels)),
		chunk('IEND', Buffer.alloc(0))
	]);
}

/** A PNG chunk: length, type, data, then a CRC over the type and data. */
function chunk(type: string, data: Buffer): Buffer {
	const typeAndData = Buffer.concat([Buffer.from(type, 'ascii'), data]);
	const length = Buffer.alloc(4);
	length.writeUInt32BE(data.length);
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(typeAndData));
	return Buffer.concat([length, typeAndData, crc]);
}

/** 32-bit FNV-1a: a small, stable hash, so the same title always gives the same colours. */
function fnv1a(text: string): number {
	let hash = 0x811c9dc5;
	for (const byte of Buffer.from(text, 'utf8')) {
		hash = Math.imul(hash ^ byte, 0x01000193) >>> 0;
	}
	return hash;
}

/** HSL (hue in degrees, saturation and lightness 0 to 1) to RGB bytes. */
function hsl(hue: number, saturation: number, lightness: number): [number, number, number] {
	const a = saturation * Math.min(lightness, 1 - lightness);
	const channel = (n: number) => {
		const k = (n + hue / 30) % 12;
		return Math.round(255 * (lightness - a * Math.max(-1, Math.min(k - 3, 9 - k, 1))));
	};
	return [channel(0), channel(8), channel(4)];
}
