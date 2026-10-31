import type { TypedDocumentString } from './generated/graphql';

export type ErrorCode =
	| 'BAD_REQUEST'
	| 'NOT_FOUND'
	| 'CONFLICT'
	| 'FAILED_PRECONDITION'
	| 'RESOURCE_EXHAUSTED'
	| 'SERVICE_UNAVAILABLE'
	| 'INTERNAL_ERROR'
	| 'NETWORK';

export interface FieldError {
	field: string;
	message: string;
}

export class GraphQlRequestError extends Error {
	constructor(
		message: string,
		readonly code: ErrorCode,
		readonly fieldErrors: FieldError[] = []
	) {
		super(message);
		this.name = 'GraphQlRequestError';
	}

	fieldMessages(): Record<string, string> {
		return Object.fromEntries(this.fieldErrors.map((e) => [e.field, e.message]));
	}
}

interface GraphQlErrorJson {
	message?: string;
	extensions?: { code?: string; fieldErrors?: FieldError[] };
}

interface GraphQlResponseJson<T> {
	data?: T | null;
	errors?: GraphQlErrorJson[];
}

/** GraphQL reports errors with HTTP 200, so the body is always checked. */
export async function request<TResult, TVariables>(
	document: TypedDocumentString<TResult, TVariables>,
	variables?: TVariables,
	init: { signal?: AbortSignal; fetch?: typeof fetch } = {}
): Promise<TResult> {
	const doFetch = init.fetch ?? fetch;
	let response: Response;
	try {
		response = await doFetch('/graphql', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
			body: JSON.stringify({ query: document.toString(), variables }),
			signal: init.signal
		});
	} catch (e) {
		if (e instanceof DOMException && e.name === 'AbortError') throw e;
		throw new GraphQlRequestError(
			'Could not reach the server. Check your connection and try again.',
			'NETWORK'
		);
	}
	return parseResponse<TResult>(response.status, await response.text());
}

export function parseResponse<TResult>(status: number, text: string): TResult {
	let body: GraphQlResponseJson<TResult>;
	try {
		body = JSON.parse(text);
	} catch {
		throw new GraphQlRequestError(
			`The server returned an unexpected response (HTTP ${status}).`,
			status >= 500 ? 'SERVICE_UNAVAILABLE' : 'INTERNAL_ERROR'
		);
	}
	const first = body.errors?.[0];
	if (first) {
		const code = (first.extensions?.code as ErrorCode | undefined) ?? 'INTERNAL_ERROR';
		throw new GraphQlRequestError(
			first.message ?? 'Something went wrong',
			code,
			first.extensions?.fieldErrors ?? []
		);
	}
	if (body.data == null) {
		throw new GraphQlRequestError(
			`The server returned no data (HTTP ${status}).`,
			'INTERNAL_ERROR'
		);
	}
	return body.data;
}

export function describeError(error: unknown): string {
	if (error instanceof GraphQlRequestError) {
		switch (error.code) {
			case 'CONFLICT':
				return 'Someone else changed this first. Reload to see the latest version, then try again.';
			case 'SERVICE_UNAVAILABLE':
				return 'The server did not respond in time. Reload to check whether your change was saved.';
			default:
				return error.message;
		}
	}
	return 'Something went wrong. Please try again.';
}

/** For failed reads: nothing was being saved, so a timeout just asks to try again. */
export function describeReadError(error: unknown): string {
	if (error instanceof GraphQlRequestError && error.code === 'SERVICE_UNAVAILABLE') {
		return 'The server did not respond in time. Try again.';
	}
	return describeError(error);
}
