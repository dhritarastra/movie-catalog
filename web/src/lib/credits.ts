import type { CreditType } from './graphql/generated/graphql.ts';

export const CREDIT_TYPES: readonly CreditType[] = ['CAST', 'DIRECTOR', 'WRITER', 'PRODUCER'];

const LABELS: Record<CreditType, string> = {
	CAST: 'Cast',
	DIRECTOR: 'Director',
	WRITER: 'Writer',
	PRODUCER: 'Producer'
};

export function roleLabel(type: CreditType): string {
	return LABELS[type];
}

/** "as Louise Banks" for a named cast role, otherwise the role name. */
export function describeRole(credit: { type: CreditType; characterName: string | null }): string {
	return credit.type === 'CAST' && credit.characterName
		? `as ${credit.characterName}`
		: roleLabel(credit.type);
}

export const MAX_BILLING_ORDER = 1000;

/** Drops credits whose movie or person was deleted while the response was built. */
export function resolved<T, K extends keyof T>(
	credits: T[],
	key: K
): (T & { [P in K]: NonNullable<T[P]> })[] {
	return credits.filter(
		(credit): credit is T & { [P in K]: NonNullable<T[P]> } => credit[key] != null
	);
}

export interface CreditFormValues {
	personId: string;
	type: CreditType;
	characterName: string;
	billingOrder: number | null;
}

/** Quick checks in the browser; the server has the final say. */
export function validateCredit(
	values: CreditFormValues,
	needsPerson: boolean
): Record<string, string> {
	const found: Record<string, string> = {};
	if (needsPerson && !values.personId) found.personId = 'Choose a person.';
	if (values.type === 'CAST' && values.characterName.trim().length > 200)
		found.characterName = 'Use at most 200 characters.';
	const order = values.billingOrder;
	if (order === null || !Number.isInteger(order) || order < 0 || order > MAX_BILLING_ORDER)
		found.billingOrder = `Enter a whole number from 0 to ${MAX_BILLING_ORDER}.`;
	return found;
}

/** Only cast credits have a character, so it's dropped for other roles. */
export function creditFields(values: CreditFormValues): {
	type: CreditType;
	characterName: string | null;
	billingOrder: number;
} {
	return {
		type: values.type,
		characterName: values.type === 'CAST' ? values.characterName.trim() || null : null,
		billingOrder: values.billingOrder ?? 0
	};
}
