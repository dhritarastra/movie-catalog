import { describeError, GraphQlRequestError } from './graphql/client.ts';

export type SaveFailure =
	| { kind: 'fields'; errors: Record<string, string> }
	| { kind: 'form'; message: string; conflict: boolean };

/** Field errors are shown next to inputs, unless one names a field the form doesn't have. */
export function saveFailure(
	error: unknown,
	formFields: readonly string[],
	conflictMessage: string
): SaveFailure {
	if (error instanceof GraphQlRequestError) {
		const fieldErrors = error.fieldErrors;
		if (fieldErrors.length > 0 && fieldErrors.every((e) => formFields.includes(e.field))) {
			return { kind: 'fields', errors: error.fieldMessages() };
		}
		if (error.code === 'CONFLICT') {
			return { kind: 'form', message: conflictMessage, conflict: true };
		}
	}
	return { kind: 'form', message: describeError(error), conflict: false };
}
