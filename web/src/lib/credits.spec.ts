import { describe, expect, it } from 'vitest';
import {
	creditFields,
	describeRole,
	resolved,
	roleLabel,
	validateCredit,
	type CreditFormValues
} from './credits';

const valid: CreditFormValues = {
	personId: 'amy',
	type: 'CAST',
	characterName: 'Louise Banks',
	billingOrder: 0
};

describe('roleLabel and describeRole', () => {
	it('names each role', () => {
		expect(roleLabel('CAST')).toBe('Cast');
		expect(roleLabel('PRODUCER')).toBe('Producer');
	});

	it('describes a cast credit by its character, and anything else by its role', () => {
		expect(describeRole({ type: 'CAST', characterName: 'Louise Banks' })).toBe('as Louise Banks');
		expect(describeRole({ type: 'CAST', characterName: null })).toBe('Cast');
		expect(describeRole({ type: 'DIRECTOR', characterName: null })).toBe('Director');
	});
});

describe('validateCredit', () => {
	it('accepts a complete credit, and billing order 0', () => {
		expect(validateCredit(valid, true)).toEqual({});
	});

	it('requires a person when adding, but not when editing', () => {
		const noPerson = { ...valid, personId: '' };

		expect(validateCredit(noPerson, true)).toEqual({ personId: 'Choose a person.' });
		expect(validateCredit(noPerson, false)).toEqual({});
	});

	it('accepts a character name of 200 characters and rejects 201, after trimming', () => {
		expect(validateCredit({ ...valid, characterName: ` ${'x'.repeat(200)} ` }, true)).toEqual({});
		expect(validateCredit({ ...valid, characterName: 'x'.repeat(201) }, true)).toEqual({
			characterName: 'Use at most 200 characters.'
		});
	});

	it('ignores a long character name on a creator role, because it is not sent', () => {
		expect(
			validateCredit({ ...valid, type: 'WRITER', characterName: 'x'.repeat(500) }, true)
		).toEqual({});
	});

	it('accepts billing order 1000 and rejects an empty, negative, fractional or larger one', () => {
		expect(validateCredit({ ...valid, billingOrder: 1000 }, true)).toEqual({});
		for (const billingOrder of [null, -1, 1.5, 1001]) {
			expect(validateCredit({ ...valid, billingOrder }, true)).toEqual({
				billingOrder: 'Enter a whole number from 0 to 1000.'
			});
		}
	});
});

describe('creditFields', () => {
	it('trims the character name and sends a blank one as none', () => {
		expect(creditFields({ ...valid, characterName: '  Louise Banks ' }).characterName).toBe(
			'Louise Banks'
		);
		expect(creditFields({ ...valid, characterName: '   ' }).characterName).toBeNull();
	});

	it('drops a character typed before switching to a creator role', () => {
		expect(creditFields({ ...valid, type: 'DIRECTOR' })).toEqual({
			type: 'DIRECTOR',
			characterName: null,
			billingOrder: 0
		});
	});
});

describe('resolved', () => {
	it('keeps credits whose movie or person was found, and drops ones deleted mid-request', () => {
		const credits = [
			{ id: 'a', person: { name: 'Amy Adams' } },
			{ id: 'b', person: null }
		];

		expect(resolved(credits, 'person').map((c) => c.id)).toEqual(['a']);
	});
});
