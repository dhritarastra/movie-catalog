import { parseResponse, GraphQlRequestError } from './client';
import type { MovieDetailFieldsFragment } from './generated/graphql';
import { UploadArtworkMutation } from './operations';

export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
export const ACCEPTED_TYPES = ['image/jpeg', 'image/png'];

export function checkFile(file: File): string | null {
	if (!ACCEPTED_TYPES.includes(file.type)) return 'Choose a JPEG or PNG image.';
	if (file.size === 0) return 'That file is empty.';
	if (file.size > MAX_UPLOAD_BYTES) return 'The image must be at most 5 MB.';
	return null;
}

export interface UploadCallbacks {
	onProgress(fraction: number): void;
	/** Every byte is sent; the server is still processing it. */
	onProcessing(): void;
}

export interface UploadHandle {
	result: Promise<MovieDetailFieldsFragment>;
	cancel(): void;
}

/** Uses XMLHttpRequest because fetch cannot report upload progress. */
export function uploadArtwork(
	movieId: string,
	version: number,
	file: File,
	callbacks: UploadCallbacks,
	createXhr: () => XMLHttpRequest = () => new XMLHttpRequest()
): UploadHandle {
	const form = new FormData();
	form.append(
		'operations',
		JSON.stringify({
			query: UploadArtworkMutation.toString(),
			variables: { movieId, version, file: null }
		})
	);
	form.append('map', JSON.stringify({ '0': ['variables.file'] }));
	form.append('0', file, file.name);

	const xhr = createXhr();
	const result = new Promise<MovieDetailFieldsFragment>((resolve, reject) => {
		xhr.upload.onprogress = (event) => {
			if (event.lengthComputable) callbacks.onProgress(event.loaded / event.total);
		};
		xhr.upload.onload = () => callbacks.onProcessing();
		xhr.onload = () => {
			try {
				resolve(
					parseResponse<{ uploadArtwork: MovieDetailFieldsFragment }>(xhr.status, xhr.responseText)
						.uploadArtwork
				);
			} catch (e) {
				reject(e);
			}
		};
		xhr.onerror = () =>
			reject(
				new GraphQlRequestError(
					'The upload failed. Check your connection and try again.',
					'NETWORK'
				)
			);
		xhr.onabort = () => reject(new DOMException('Upload cancelled', 'AbortError'));
	});
	xhr.open('POST', '/graphql');
	xhr.setRequestHeader('Accept', 'application/json');
	xhr.send(form);

	return { result, cancel: () => xhr.abort() };
}
