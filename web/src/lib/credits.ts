import type { CreditType } from './graphql/generated/graphql.ts';

/** The roles in the order a form offers them: cast first, then the creator roles. */
export const CREDIT_TYPES: readonly CreditType[] = ['CAST', 'DIRECTOR', 'WRITER', 'PRODUCER'];

const LABELS: Record<CreditType, string> = {
	CAST: 'Cast',
	DIRECTOR: 'Director',
	WRITER: 'Writer',
	PRODUCER: 'Producer'
};

/** CAST -> "Cast", DIRECTOR -> "Director". */
export function roleLabel(type: CreditType): string {
	return LABELS[type];
}

/** "as Louise Banks" for a named cast role, otherwise the role itself ("Cast", "Director"). */
export function describeRole(credit: { type: CreditType; characterName: string | null }): string {
	return credit.type === 'CAST' && credit.characterName
		? `as ${credit.characterName}`
		: roleLabel(credit.type);
}

/** The same limit as CreditInput and V3__create_credit.sql in the catalog. */
export const MAX_BILLING_ORDER = 1000;

/**
 * The credits whose `movie` or `person` was found. Either is null only if it was deleted while
 * the response was being built, and such a credit is gone by then, so it is left out.
 */
export function resolved<T, K extends keyof T>(
	credits: T[],
	key: K
): (T & { [P in K]: NonNullable<T[P]> })[] {
	return credits.filter(
		(credit): credit is T & { [P in K]: NonNullable<T[P]> } => credit[key] != null
	);
}

/** What the credit form holds; `billingOrder` is null while its input is empty. */
export interface CreditFormValues {
	personId: string;
	type: CreditType;
	characterName: string;
	billingOrder: number | null;
}

/**
 * Mirrors the catalog's rules for instant feedback; the catalog still has the final say.
 * `needsPerson` is false when editing, because a credit's person cannot change.
 */
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

/**
 * The role, character and billing order to send. Only cast credits name a character, so a
 * character typed before switching to a creator role is dropped rather than refused.
 */
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
