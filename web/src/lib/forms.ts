import { describeError, GraphQlRequestError } from './graphql/client.ts';

/** What a form shows after a failed save: messages next to its fields, or one message for the form. */
export type SaveFailure =
	| { kind: 'fields'; errors: Record<string, string> }
	| { kind: 'form'; message: string; conflict: boolean };

/**
 * Decides how a form presents a failed save. Field errors are shown next to inputs only when
 * the form has every field they name; otherwise (for example an error about the version) they
 * would be invisible, so the message is shown for the whole form instead.
 */
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
