import { describe, expect, it } from 'vitest';
import { saveFailure } from './forms';
import { GraphQlRequestError } from './graphql/client';

const fields = ['name', 'birthDate', 'bio'] as const;
const conflict = 'Someone else changed this person.';

describe('saveFailure', () => {
	it('puts field errors next to the fields they name', () => {
		const error = new GraphQlRequestError('invalid', 'BAD_REQUEST', [
			{ field: 'name', message: 'is required' },
			{ field: 'birthDate', message: 'cannot be in the future' }
		]);

		expect(saveFailure(error, fields, conflict)).toEqual({
			kind: 'fields',
			errors: { name: 'is required', birthDate: 'cannot be in the future' }
		});
	});

	it('shows the message for the whole form when an error names a field the form does not have', () => {
		const error = new GraphQlRequestError('expected_version: must be at least 1', 'BAD_REQUEST', [
			{ field: 'name', message: 'is required' },
			{ field: 'expectedVersion', message: 'must be at least 1' }
		]);

		expect(saveFailure(error, fields, conflict)).toEqual({
			kind: 'form',
			message: 'expected_version: must be at least 1',
			conflict: false
		});
	});

	it('marks a conflict so the form can offer to reload', () => {
		expect(saveFailure(new GraphQlRequestError('stale', 'CONFLICT'), fields, conflict)).toEqual({
			kind: 'form',
			message: conflict,
			conflict: true
		});
	});

	it('explains other failures without claiming nothing was saved', () => {
		const timeout = saveFailure(
			new GraphQlRequestError('late', 'SERVICE_UNAVAILABLE'),
			fields,
			conflict
		);
		expect(timeout).toMatchObject({ kind: 'form', conflict: false });
		expect(timeout.kind === 'form' && timeout.message).toMatch(
			/check whether your change was saved/
		);

		expect(saveFailure(new TypeError('boom'), fields, conflict)).toEqual({
			kind: 'form',
			message: 'Something went wrong. Please try again.',
			conflict: false
		});
	});
});
