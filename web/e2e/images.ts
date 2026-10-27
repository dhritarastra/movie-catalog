import { deflateSync } from 'node:zlib';

/** A real RGB PNG of the given size, built in code so the test needs no binary fixtures. */
export function png(width: number, height: number): Buffer {
	const row = Buffer.alloc(1 + width * 3);
	const rows: Buffer[] = [];
	for (let y = 0; y < height; y++) {
		row[0] = 0; // no filter
		for (let x = 0; x < width; x++) {
			row[1 + x * 3] = (x * 255) / width;
			row[2 + x * 3] = 90;
			row[3 + x * 3] = (y * 255) / height;
		}
		rows.push(Buffer.from(row));
	}
	const header = Buffer.alloc(13);
	header.writeUInt32BE(width, 0);
	header.writeUInt32BE(height, 4);
	header.set([8, 2, 0, 0, 0], 8); // 8-bit, truecolour, default compression/filter/interlace
	return Buffer.concat([
		Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
		chunk('IHDR', header),
		chunk('IDAT', deflateSync(Buffer.concat(rows))),
		chunk('IEND', Buffer.alloc(0))
	]);
}

function chunk(type: string, data: Buffer): Buffer {
	const length = Buffer.alloc(4);
	length.writeUInt32BE(data.length);
	const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(body));
	return Buffer.concat([length, body, crc]);
}

function crc32(bytes: Buffer): number {
	let crc = 0xffffffff;
	for (const byte of bytes) {
		crc ^= byte;
		for (let k = 0; k < 8; k++) crc = crc & 1 ? (crc >>> 1) ^ 0xedb88320 : crc >>> 1;
	}
	return (crc ^ 0xffffffff) >>> 0;
}
