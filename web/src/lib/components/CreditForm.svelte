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
	import { describeError, describeReadError, request } from '#lib/graphql/client.ts';
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

	// Only an added credit needs a person chosen: they are found by typing part of their name.
	const SEARCH_DELAY_MS = 250;
	type Matches =
		| { status: 'loading' }
		| { status: 'error'; message: string }
		| { status: 'ready'; text: string; people: { id: string; name: string }[]; total: number };
	let query = $state('');
	let matches = $state<Matches>({ status: 'loading' });
	let searching = $state(false);
	let chosenName = $state<string | null>(null);
	let attempt = $state(0);

	// Derived values only notify when they change, so a trailing space does not search again.
	const searchText = $derived(query.trim());

	$effect(() => {
		if (!adding) return;
		const text = searchText;
		void attempt;
		const controller = new AbortController();
		// Waits for a pause in typing. The cleanup cancels both the wait and the request it
		// started, so an older, slower answer can never replace a newer one.
		const timer = setTimeout(
			() => {
				searching = true;
				request(PersonPickerQuery, { nameContains: text || null }, { signal: controller.signal })
					.then((data) => {
						matches = {
							status: 'ready',
							text,
							people: data.people.items,
							total: data.people.totalCount
						};
					})
					.catch((error) => {
						if (error?.name !== 'AbortError')
							matches = { status: 'error', message: describeReadError(error) };
					})
					.finally(() => {
						if (!controller.signal.aborted) searching = false;
					});
			},
			text ? SEARCH_DELAY_MS : 0
		);
		return () => {
			clearTimeout(timer);
			controller.abort();
		};
	});

	// Opening the form moves focus into it; when editing, the person is fixed, so the role is first.
	onMount(() => focusField(adding ? 'personId' : 'type'));

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
		<div class="field">
			<label for="personId" id="personId-label"
				>Person {#if adding}<span aria-hidden="true">*</span>{/if}</label
			>
			{#if !adding}
				<input id="personId" value={person?.name} readonly />
			{:else}
				<input
					id="personId"
					type="search"
					bind:value={query}
					maxlength="200"
					autocomplete="off"
					placeholder="Type part of a name"
					aria-invalid={!!errors.personId}
					aria-describedby={errors.personId ? 'personId-status personId-error' : 'personId-status'}
				/>
				<!-- Everything a search changes is announced here, so the results are heard as well as seen. -->
				<p class="muted hint" id="personId-status" aria-live="polite">
					{#if chosenName}Chosen: <strong>{chosenName}</strong>.{/if}
					{#if searching || matches.status === 'loading'}
						Searching…
					{:else if matches.status === 'ready' && matches.people.length === 0}
						{matches.text ? `No one's name contains “${matches.text}”.` : 'No people yet.'}
					{:else if matches.status === 'ready' && matches.total > matches.people.length}
						Showing {matches.people.length} of {matches.total} people{matches.text
							? ' matching'
							: ''}. Type more of the name to narrow them down.
					{/if}
				</p>
				{#if matches.status === 'error'}
					<p class="error" role="alert">{matches.message}</p>
					<button class="button" type="button" onclick={() => attempt++}>Retry</button>
				{:else if matches.status === 'ready' && matches.people.length === 0}
					<p class="muted"><a href={resolve('/people/new')}>Add a person</a> first.</p>
				{:else if matches.status === 'ready'}
					<div
						class="matches"
						role="radiogroup"
						aria-labelledby="personId-label personId-matches"
						aria-required="true"
					>
						<span class="visually-hidden" id="personId-matches">matching people</span>
						{#each matches.people as match (match.id)}
							<label class="match">
								<input
									type="radio"
									name="credit-person"
									value={match.id}
									bind:group={personId}
									onchange={() => (chosenName = match.name)}
								/>
								{match.name}
							</label>
						{/each}
					</div>
				{/if}
			{/if}
			{#if errors.personId}<p class="error" id="personId-error">{errors.personId}</p>{/if}
		</div>

		<div class="row">
			<div class="field">
				<label for="type">Role</label>
				<select id="type" bind:value={type}>
					{#each CREDIT_TYPES as role (role)}<option value={role}>{roleLabel(role)}</option>{/each}
				</select>
				{#if errors.type}<p class="error" id="type-error">{errors.type}</p>{/if}
			</div>

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
		</div>

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
	</fieldset>

	<div class="actions">
		<button class="button" type="button" onclick={onCancel} disabled={saving}>Cancel</button>
		<button class="button primary" type="submit" disabled={saving}>
			{saving ? 'Saving…' : submitLabel}
		</button>
	</div>
</form>

<style>
	.matches {
		display: grid;
		gap: 0.25rem;
		max-height: 16rem;
		overflow-y: auto;
		padding: 0.25rem 0;
	}

	.match {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		font-weight: 400;
		overflow-wrap: anywhere;
	}

	/* The form's text-input sizing does not apply to radio buttons. */
	.match input {
		width: auto;
		min-height: auto;
		margin: 0;
	}
</style>
