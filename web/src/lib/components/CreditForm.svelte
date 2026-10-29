<script lang="ts">
	import { onMount, tick, untrack } from 'svelte';
	import { resolve } from '$app/paths';
	import {
		CREDIT_TYPES,
		MAX_BILLING_ORDER,
		roleLabel,
		validateCredit,
		type CreditFormValues
	} from '#lib/credits.ts';
	import { saveFailure, type SaveFailure } from '#lib/forms.ts';
	import { describeError, request } from '#lib/graphql/client.ts';
	import { PersonPickerQuery } from '#lib/graphql/operations.ts';
	import { trackVisit } from '#lib/navigation.ts';
	import { showToast } from '#lib/toasts.svelte.ts';

	interface Props {
		/** Shown read-only when editing, because a credit's person cannot change. */
		person?: { name: string };
		/** Starting values: empty when adding, the stored credit when editing. */
		initial?: CreditFormValues;
		submitLabel: string;
		/** Saves the values; a rejection is shown as field errors or a message. */
		onSubmit(values: CreditFormValues): Promise<void>;
		onCancel(): void;
	}

	let { person, initial, submitLabel, onSubmit, onCancel }: Props = $props();

	const FIELDS = ['personId', 'type', 'characterName', 'billingOrder'];
	const adding = untrack(() => !person);

	// Copied once, like MovieForm: the form owns its values after it is created.
	const start = untrack(() => initial);
	let personId = $state(start?.personId ?? '');
	let type = $state(start?.type ?? 'CAST');
	let characterName = $state(start?.characterName ?? '');
	let billingOrder = $state<number | null>(start?.billingOrder ?? 0);

	let saving = $state(false);
	let errors = $state<Record<string, string>>({});
	let formError = $state<string | null>(null);
	let alert: HTMLElement | undefined = $state();
	let form: HTMLFormElement | undefined = $state();
	const visit = trackVisit();

	type Choices =
		| { status: 'loading' }
		| { status: 'error'; message: string }
		| { status: 'ready'; people: { id: string; name: string }[]; total: number };
	let choices = $state<Choices>({ status: 'loading' });
	let attempt = $state(0);

	// Only an added credit needs a person chosen. Until search exists, the first 100 people
	// alphabetically are offered.
	$effect(() => {
		if (!adding) return;
		void attempt;
		const controller = new AbortController();
		choices = { status: 'loading' };
		request(PersonPickerQuery, {}, { signal: controller.signal })
			.then(async (data) => {
				choices = { status: 'ready', people: data.people.items, total: data.people.totalCount };
				// The list was disabled while loading, so focus moves into the form once it can.
				await tick();
				focusField('personId');
			})
			.catch((error) => {
				if (error?.name !== 'AbortError')
					choices = { status: 'error', message: describeError(error) };
			});
		return () => controller.abort();
	});

	// Opening the form moves focus into it; when editing, the person is fixed, so the role is first.
	onMount(() => {
		if (!adding) focusField('type');
	});

	function focusField(id: string) {
		form?.querySelector<HTMLElement>(`#${id}`)?.focus();
	}

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		if (saving) return;
		const values = { personId, type, characterName, billingOrder };
		errors = validateCredit(values, adding);
		formError = null;
		if (Object.keys(errors).length > 0) {
			form?.querySelector<HTMLElement>(`#${Object.keys(errors)[0]}`)?.focus();
			return;
		}
		saving = true;
		const stillHere = visit.mark();
		let failure: SaveFailure | null = null;
		try {
			await onSubmit(values);
		} catch (error) {
			if (!stillHere()) {
				showToast('error', `Credit: ${describeError(error)}`);
				return;
			}
			failure = saveFailure(error, FIELDS, 'This person already has this role on this movie.');
		} finally {
			if (stillHere()) saving = false;
		}
		if (failure && stillHere()) await showFailure(failure, stillHere);
	}

	/** Locking the fields took focus off the form, so focus goes back to what needs attention. */
	async function showFailure(failure: SaveFailure, stillHere: () => boolean) {
		if (failure.kind === 'fields') errors = failure.errors;
		else formError = failure.message;
		await tick();
		if (!stillHere()) return;
		if (failure.kind === 'fields')
			form?.querySelector<HTMLElement>(`#${Object.keys(errors)[0]}`)?.focus();
		else alert?.focus();
	}
</script>

<form bind:this={form} class="panel form" onsubmit={submit} novalidate aria-busy={saving}>
	{#if formError}
		<div class="alert" role="alert" tabindex="-1" bind:this={alert}><p>{formError}</p></div>
	{/if}

	<fieldset disabled={saving}>
		<div class="row">
			<div class="field">
				<label for="personId"
					>Person {#if adding}<span aria-hidden="true">*</span>{/if}</label
				>
				{#if !adding}
					<input id="personId" value={person?.name} readonly />
				{:else if choices.status === 'loading'}
					<select id="personId" disabled aria-busy="true"><option>Loading people…</option></select>
				{:else if choices.status === 'error'}
					<p class="error" role="alert">{choices.message}</p>
					<button class="button" type="button" onclick={() => attempt++}>Retry</button>
				{:else if choices.people.length === 0}
					<p class="muted">
						No people yet. <a href={resolve('/people/new')}>Add a person</a> first.
					</p>
				{:else}
					<select
						id="personId"
						bind:value={personId}
						required
						aria-invalid={!!errors.personId}
						aria-describedby={errors.personId ? 'personId-error' : undefined}
					>
						<option value="" disabled>Choose a person</option>
						{#each choices.people as choice (choice.id)}
							<option value={choice.id}>{choice.name}</option>
						{/each}
					</select>
					{#if choices.total > choices.people.length}
						<p class="muted hint">
							Showing the first {choices.people.length} of {choices.total} people, by name.
						</p>
					{/if}
				{/if}
				{#if errors.personId}<p class="error" id="personId-error">{errors.personId}</p>{/if}
			</div>

			<div class="field">
				<label for="type">Role</label>
				<select id="type" bind:value={type}>
					{#each CREDIT_TYPES as role (role)}<option value={role}>{roleLabel(role)}</option>{/each}
				</select>
				{#if errors.type}<p class="error" id="type-error">{errors.type}</p>{/if}
			</div>
		</div>

		<div class="row">
			{#if type === 'CAST'}
				<div class="field">
					<label for="characterName">Character</label>
					<input
						id="characterName"
						bind:value={characterName}
						maxlength="200"
						autocomplete="off"
						aria-invalid={!!errors.characterName}
						aria-describedby={errors.characterName ? 'characterName-error' : undefined}
					/>
					{#if errors.characterName}
						<p class="error" id="characterName-error">{errors.characterName}</p>
					{/if}
				</div>
			{/if}

			<div class="field">
				<label for="billingOrder">Billing order</label>
				<input
					id="billingOrder"
					type="number"
					min="0"
					max={MAX_BILLING_ORDER}
					step="1"
					bind:value={billingOrder}
					aria-invalid={!!errors.billingOrder}
					aria-describedby={errors.billingOrder ? 'billingOrder-error' : 'billingOrder-hint'}
				/>
				{#if errors.billingOrder}
					<p class="error" id="billingOrder-error">{errors.billingOrder}</p>
				{:else}
					<p class="muted hint" id="billingOrder-hint">Lower numbers are listed first.</p>
				{/if}
			</div>
		</div>
	</fieldset>

	<div class="actions">
		<button class="button" type="button" onclick={onCancel} disabled={saving}>Cancel</button>
		<button class="button primary" type="submit" disabled={saving}>
			{saving ? 'Saving…' : submitLabel}
		</button>
	</div>
</form>
