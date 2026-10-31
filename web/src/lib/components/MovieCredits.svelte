<script lang="ts">
	import { resolve } from '$app/paths';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import CreditForm from '#lib/components/CreditForm.svelte';
	import {
		creditFields,
		describeRole,
		resolved,
		roleLabel,
		type CreditFormValues
	} from '#lib/credits.ts';
	import { tick } from 'svelte';
	import {
		describeError,
		describeReadError,
		GraphQlRequestError,
		request
	} from '#lib/graphql/client.ts';
	import type { CreditFieldsFragment } from '#lib/graphql/generated/graphql.ts';
	import {
		AddCreditMutation,
		MovieCreditsQuery,
		RemoveCreditMutation,
		UpdateCreditMutation
	} from '#lib/graphql/operations.ts';
	import { trackVisit } from '#lib/navigation.ts';
	import { showToast } from '#lib/toasts.svelte.ts';

	interface Props {
		movieId: string;
	}

	let { movieId }: Props = $props();

	type Credit = CreditFieldsFragment & { person: NonNullable<CreditFieldsFragment['person']> };

	type View =
		| { status: 'loading' }
		| { status: 'error'; message: string }
		| { status: 'ready'; cast: Credit[]; creators: Credit[] };

	let view = $state<View>({ status: 'loading' });
	let attempt = $state(0);
	const visit = trackVisit();

	$effect(() => {
		const id = movieId;
		void attempt;
		const controller = new AbortController();
		view = { status: 'loading' };
		editing = null;
		removing = null;
		load(id, controller.signal).catch((error) => {
			if (error?.name !== 'AbortError')
				view = { status: 'error', message: describeReadError(error) };
		});
		return () => controller.abort();
	});

	async function load(id: string, signal?: AbortSignal) {
		const data = await request(MovieCreditsQuery, { id }, { signal });
		view = {
			status: 'ready',
			cast: resolved(data.movie?.cast ?? [], 'person'),
			creators: resolved(data.movie?.creators ?? [], 'person')
		};
	}

	async function refresh(stillHere: () => boolean) {
		if (!stillHere()) return;
		try {
			await load(movieId);
		} catch (error) {
			if (stillHere())
				showToast('error', `The credits could not be reloaded: ${describeReadError(error)}`);
		}
	}

	// One form at a time.
	let editing = $state<{ mode: 'add' } | { mode: 'edit'; credit: Credit } | null>(null);
	let addButton: HTMLButtonElement | undefined = $state();

	/** The form or the edited row may be gone, so focus "Add credit". */
	async function closeForm() {
		editing = null;
		await tick();
		addButton?.focus();
	}

	async function add(values: CreditFormValues) {
		const stillHere = visit.mark();
		const { addCredit } = await request(AddCreditMutation, {
			input: { movieId, personId: values.personId, ...creditFields(values) }
		});
		showToast(
			'success',
			`${addCredit.person?.name ?? 'The person'} was credited (${roleLabel(addCredit.type)}).`
		);
		await refresh(stillHere);
		if (stillHere()) await closeForm();
	}

	async function update(credit: Credit, values: CreditFormValues) {
		const stillHere = visit.mark();
		try {
			await request(UpdateCreditMutation, { id: credit.id, input: creditFields(values) });
		} catch (error) {
			if (!(error instanceof GraphQlRequestError && error.code === 'NOT_FOUND')) throw error;
			showToast('error', `${credit.person.name}'s credit had already been removed.`);
			await refresh(stillHere);
			if (stillHere()) await closeForm();
			return;
		}
		showToast('success', `${credit.person.name}'s credit was updated.`);
		await refresh(stillHere);
		if (stillHere()) await closeForm();
	}

	let removing = $state<Credit | null>(null);
	let busy = $state(false);
	let removeError = $state<string | null>(null);

	async function remove(credit: Credit) {
		const stillHere = visit.mark();
		busy = true;
		removeError = null;
		try {
			await request(RemoveCreditMutation, { id: credit.id });
			showToast('success', `${credit.person.name} was removed from this movie.`);
			if (stillHere()) removing = null;
			await refresh(stillHere);
			if (stillHere()) await closeForm();
		} catch (error) {
			if (error instanceof GraphQlRequestError && error.code === 'NOT_FOUND') {
				showToast('success', `${credit.person.name} had already been removed from this movie.`);
				if (stillHere()) removing = null;
				await refresh(stillHere);
			} else if (stillHere()) {
				removeError = describeError(error);
			} else {
				showToast('error', `Removing ${credit.person.name}: ${describeError(error)}`);
			}
		} finally {
			if (stillHere()) busy = false;
		}
	}

	function formValues(credit: Credit): CreditFormValues {
		return {
			personId: credit.person.id,
			type: credit.type,
			characterName: credit.characterName ?? '',
			billingOrder: credit.billingOrder
		};
	}
</script>

{#snippet creditList(credits: Credit[], label: string, empty: string)}
	{#if credits.length === 0}
		<p class="muted">{empty}</p>
	{:else}
		<ul class="credits" aria-label={label}>
			{#each credits as credit (credit.id)}
				<li>
					<span>
						<a href={resolve('/people/[id]', { id: credit.person.id })}>{credit.person.name}</a>
						<span class="muted">{describeRole(credit)}</span>
					</span>
					<span class="actions">
						<button
							class="button link edit"
							type="button"
							disabled={editing !== null}
							aria-label="Edit {credit.person.name}'s credit ({roleLabel(credit.type)})"
							onclick={() => (editing = { mode: 'edit', credit })}>Edit</button
						>
						<button
							class="button link"
							type="button"
							disabled={editing !== null}
							aria-label="Remove {credit.person.name} ({roleLabel(credit.type)})"
							onclick={() => {
								removeError = null;
								removing = credit;
							}}>Remove</button
						>
					</span>
				</li>
			{/each}
		</ul>
	{/if}
{/snippet}

<section class="credits-section" aria-labelledby="credits-heading">
	<div class="heading-row">
		<h2 id="credits-heading">Cast and creators</h2>
		{#if view.status === 'ready' && !editing}
			<button
				class="button"
				type="button"
				bind:this={addButton}
				onclick={() => (editing = { mode: 'add' })}
			>
				Add credit
			</button>
		{/if}
	</div>

	{#if view.status === 'loading'}
		<div aria-busy="true">
			<div class="skeleton line"></div>
			<p class="visually-hidden" aria-live="polite">Loading credits</p>
		</div>
	{:else if view.status === 'error'}
		<div class="alert" role="alert">
			<p>{view.message}</p>
			<button class="button" type="button" onclick={() => attempt++}>Retry</button>
		</div>
	{:else}
		{#if editing?.mode === 'add'}
			<h3>Add a credit</h3>
			<CreditForm submitLabel="Add credit" onSubmit={add} onCancel={closeForm} />
		{:else if editing?.mode === 'edit'}
			{@const credit = editing.credit}
			<h3>Edit {credit.person.name}'s credit</h3>
			{#key credit.id}
				<CreditForm
					person={credit.person}
					initial={formValues(credit)}
					submitLabel="Save credit"
					onSubmit={(values) => update(credit, values)}
					onCancel={closeForm}
				/>
			{/key}
		{/if}

		<h3>Cast</h3>
		{@render creditList(view.cast, 'Cast', 'No cast yet.')}
		<h3>Creators</h3>
		{@render creditList(view.creators, 'Creators', 'No creators yet.')}
	{/if}
</section>

<ConfirmDialog
	open={removing !== null}
	title={removing ? `Remove ${removing.person.name} from this movie?` : ''}
	message={removing
		? `Their credit (${describeRole(removing)}) will be removed. ${removing.person.name} stays in the catalog.`
		: ''}
	confirmLabel="Remove credit"
	{busy}
	error={removeError}
	onConfirm={() => removing && remove(removing)}
	onCancel={() => !busy && (removing = null)}
/>

<style>
	.credits-section {
		display: grid;
		gap: 0.75rem;
		margin-top: 1.5rem;
	}

	.heading-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
		flex-wrap: wrap;
	}

	h2,
	h3 {
		margin: 0;
	}

	h3 {
		font-size: 1rem;
	}

	.credits {
		display: grid;
		gap: 0.25rem;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.credits li {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem;
		flex-wrap: wrap;
		padding: 0.4rem 0;
		border-bottom: 1px solid var(--border);
	}

	.credits li > span:first-child {
		display: flex;
		gap: 0.5rem;
		flex-wrap: wrap;
		overflow-wrap: anywhere;
	}

	.credits .actions {
		display: flex;
		gap: 1rem;
	}

	.credits .edit {
		color: var(--accent);
	}

	.skeleton.line {
		height: 18px;
		width: 50%;
	}

	.alert {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
		flex-wrap: wrap;
	}

	.alert p {
		margin: 0;
	}
</style>
