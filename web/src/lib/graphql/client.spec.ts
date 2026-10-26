import { describe, expect, it, vi } from 'vitest';
import { describeError, GraphQlRequestError, request } from './client';
import { MoviesQuery } from './operations';

function respond(status: number, body: string) {
	return vi.fn<typeof fetch>().mockResolvedValue(new Response(body, { status }));
}

describe('request', () => {
	it('posts the operation as JSON and returns its data', async () => {
		const fetch = respond(200, JSON.stringify({ data: { movies: { totalCount: 0 } } }));

		const data = await request(MoviesQuery, { page: { limit: 12 } }, { fetch });

		expect(data).toEqual({ movies: { totalCount: 0 } });
		const [url, init] = fetch.mock.calls[0];
		expect(url).toBe('/graphql');
		expect(init?.method).toBe('POST');
		expect(JSON.parse(init?.body as string)).toEqual({
			query: MoviesQuery.toString(),
			variables: { page: { limit: 12 } }
		});
	});

	it('treats errors in an HTTP 200 body as failures, keeping code and field errors', async () => {
		const fetch = respond(
			200,
			JSON.stringify({
				data: null,
				errors: [
					{
						message: 'invalid',
						extensions: {
							code: 'BAD_REQUEST',
							fieldErrors: [{ field: 'runtimeMinutes', message: 'must be between 1 and 1000' }]
						}
					}
				]
			})
		);

		const error = await request(MoviesQuery, {}, { fetch }).catch((e) => e);

		expect(error).toBeInstanceOf(GraphQlRequestError);
		expect(error.code).toBe('BAD_REQUEST');
		expect(error.fieldMessages()).toEqual({ runtimeMinutes: 'must be between 1 and 1000' });
	});

	it('reports a non-JSON 502 from a proxy as the service being unavailable', async () => {
		const error = await request(
			MoviesQuery,
			{},
			{ fetch: respond(502, '<html>Bad Gateway</html>') }
		).catch((e) => e);

		expect(error.code).toBe('SERVICE_UNAVAILABLE');
	});

	it('reports a failed connection as NETWORK, but lets an abort through unchanged', async () => {
		const offline = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch'));
		await expect(request(MoviesQuery, {}, { fetch: offline })).rejects.toMatchObject({
			code: 'NETWORK'
		});

		const aborted = vi
			.fn<typeof fetch>()
			.mockRejectedValue(new DOMException('aborted', 'AbortError'));
		await expect(request(MoviesQuery, {}, { fetch: aborted })).rejects.toMatchObject({
			name: 'AbortError'
		});
	});

	it('rejects a response with neither data nor errors', async () => {
		await expect(
			request(MoviesQuery, {}, { fetch: respond(200, '{"data": null}') })
		).rejects.toMatchObject({
			code: 'INTERNAL_ERROR'
		});
	});
});

describe('describeError', () => {
	it('tells the user to reload after a conflict or an unconfirmed write', () => {
		expect(describeError(new GraphQlRequestError('x', 'CONFLICT'))).toMatch(/Reload/);
		expect(describeError(new GraphQlRequestError('x', 'SERVICE_UNAVAILABLE'))).toMatch(
			/whether your change was saved/
		);
		expect(describeError(new GraphQlRequestError('Movie not found', 'NOT_FOUND'))).toBe(
			'Movie not found'
		);
		expect(describeError(new Error('boom'))).toMatch(/Something went wrong/);
	});
});
