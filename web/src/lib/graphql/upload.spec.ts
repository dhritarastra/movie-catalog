import { describe, expect, it, vi } from 'vitest';
import { GraphQlRequestError } from './client';
import { checkFile, MAX_UPLOAD_BYTES, uploadArtwork } from './upload';

/** Just enough of XMLHttpRequest to drive an upload by hand. */
class FakeXhr {
	upload: {
		onprogress: ((e: Partial<ProgressEvent>) => void) | null;
		onload: (() => void) | null;
	} = {
		onprogress: null,
		onload: null
	};
	onload: (() => void) | null = null;
	onerror: (() => void) | null = null;
	onabort: (() => void) | null = null;
	status = 0;
	responseText = '';
	method = '';
	url = '';
	body: FormData | null = null;

	open(method: string, url: string) {
		this.method = method;
		this.url = url;
	}
	setRequestHeader() {}
	send(body: FormData) {
		this.body = body;
	}
	abort() {
		this.onabort?.();
	}

	progress(loaded: number, total: number) {
		this.upload.onprogress?.({ lengthComputable: true, loaded, total });
	}
	finishTransfer() {
		this.upload.onload?.();
	}
	respond(status: number, body: unknown) {
		this.status = status;
		this.responseText = JSON.stringify(body);
		this.onload?.();
	}
}

const poster = new File([new Uint8Array([1, 2, 3])], 'poster.png', { type: 'image/png' });

function start(callbacks = { onProgress: vi.fn(), onProcessing: vi.fn() }) {
	const xhr = new FakeXhr();
	const handle = uploadArtwork(
		'movie-1',
		3,
		poster,
		callbacks,
		() => xhr as unknown as XMLHttpRequest
	);
	return { xhr, handle, callbacks };
}

describe('uploadArtwork', () => {
	it('sends a GraphQL multipart request: operations, map and the file part', async () => {
		const { xhr } = start();

		expect(xhr.method).toBe('POST');
		expect(xhr.url).toBe('/graphql');
		const operations = JSON.parse(xhr.body!.get('operations') as string);
		expect(operations.query).toContain(
			'uploadArtwork(movieId: $movieId, version: $version, file: $file)'
		);
		expect(operations.variables).toEqual({ movieId: 'movie-1', version: 3, file: null });
		expect(JSON.parse(xhr.body!.get('map') as string)).toEqual({ '0': ['variables.file'] });
		const file = xhr.body!.get('0') as File;
		expect(file.name).toBe('poster.png');
		expect(new Uint8Array(await file.arrayBuffer())).toEqual(new Uint8Array([1, 2, 3]));
	});

	it('reports transfer progress, then processing once every byte is sent', () => {
		const { xhr, callbacks } = start();

		xhr.progress(25, 100);
		xhr.progress(100, 100);
		expect(callbacks.onProcessing).not.toHaveBeenCalled();
		xhr.finishTransfer();

		expect(callbacks.onProgress.mock.calls).toEqual([[0.25], [1]]);
		expect(callbacks.onProcessing).toHaveBeenCalledOnce();
	});

	it('resolves with the updated movie only when the server confirms it', async () => {
		const { xhr, handle } = start();

		xhr.finishTransfer();
		xhr.respond(200, { data: { uploadArtwork: { id: 'movie-1', version: 4 } } });

		await expect(handle.result).resolves.toEqual({ id: 'movie-1', version: 4 });
	});

	it('rejects with the GraphQL error code, which arrives with HTTP 200', async () => {
		const { xhr, handle } = start();

		xhr.respond(200, {
			errors: [{ message: 'stale', extensions: { code: 'CONFLICT' } }],
			data: null
		});

		await expect(handle.result).rejects.toMatchObject({ code: 'CONFLICT' });
	});

	it('rejects with RESOURCE_EXHAUSTED for the 413 the gateway sends for oversized files', async () => {
		const { xhr, handle } = start();

		xhr.respond(413, {
			errors: [
				{ message: 'The image must be at most 5 MB', extensions: { code: 'RESOURCE_EXHAUSTED' } }
			]
		});

		await expect(handle.result).rejects.toMatchObject({ code: 'RESOURCE_EXHAUSTED' });
	});

	it('distinguishes a network failure from a cancelled upload', async () => {
		const failed = start();
		failed.xhr.onerror?.();
		await expect(failed.handle.result).rejects.toBeInstanceOf(GraphQlRequestError);
		await expect(failed.handle.result).rejects.toMatchObject({ code: 'NETWORK' });

		const cancelled = start();
		cancelled.handle.cancel();
		await expect(cancelled.handle.result).rejects.toMatchObject({ name: 'AbortError' });
	});
});

describe('checkFile', () => {
	const file = (size: number, type = 'image/png') =>
		new File([new Uint8Array(size)], 'f', { type });

	it('accepts JPEG and PNG up to exactly 5 MB', () => {
		expect(checkFile(file(10, 'image/jpeg'))).toBeNull();
		expect(checkFile(file(MAX_UPLOAD_BYTES))).toBeNull();
	});

	it('rejects other types, empty files and anything over 5 MB', () => {
		expect(checkFile(file(10, 'image/svg+xml'))).toMatch(/JPEG or PNG/);
		expect(checkFile(file(10, 'image/gif'))).toMatch(/JPEG or PNG/);
		expect(checkFile(file(0))).toMatch(/empty/);
		expect(checkFile(file(MAX_UPLOAD_BYTES + 1))).toMatch(/5 MB/);
	});
});
