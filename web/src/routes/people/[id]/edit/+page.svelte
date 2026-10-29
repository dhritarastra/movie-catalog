<script lang="ts">
	import { resolve } from '$app/paths';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import PersonForm from '#lib/components/PersonForm.svelte';
	import { describeReadError, request } from '#lib/graphql/client.ts';
	import type { PersonDetailFieldsFragment, PersonInput } from '#lib/graphql/generated/graphql.ts';
	import { PersonQuery, UpdatePersonMutation } from '#lib/graphql/operations.ts';
	import { trackVisit } from '#lib/navigation.ts';
	import { showToast } from '#lib/toasts.svelte.ts';

	type View =
		| { status: 'loading' }
		| { status: 'not-found' }
		| { status: 'error'; message: string }
		| { status: 'ready'; person: PersonDetailFieldsFragment };

	const id = $derived(page.params.id ?? '');
	let view = $state<View>({ status: 'loading' });
	let attempt = $state(0);

	$effect(() => {
		const personId = id;
		void attempt;
		const controller = new AbortController();
		view = { status: 'loading' };
		request(PersonQuery, { id: personId }, { signal: controller.signal })
			.then(
				(data) =>
					(view = data.person ? { status: 'ready', person: data.person } : { status: 'not-found' })
			)
			.catch((error) => {
				if (error?.name !== 'AbortError')
					view = { status: 'error', message: describeReadError(error) };
			});
		return () => controller.abort();
	});

	const visit = trackVisit();

	/** Saves against the version that was loaded; like the movie edit page, never pulls a user back who left. */
	async function save(person: PersonDetailFieldsFragment, input: PersonInput) {
		const stillHere = visit.mark();
		await request(UpdatePersonMutation, { id: person.id, version: person.version, input });
		showToast('success', 'Changes saved.');
		if (stillHere()) await goto(resolve('/people/[id]', { id: person.id }));
	}
</script>

<svelte:head>
	<title
		>{view.status === 'ready' ? `Edit ${view.person.name}` : 'Edit person'} · Movie Catalog</title
	>
</svelte:head>

<p><a href={resolve('/people/[id]', { id })}>← Back to person</a></p>

{#if view.status === 'loading'}
	<div aria-busy="true">
		<div class="skeleton heading"></div>
		<div class="skeleton form"></div>
		<p class="visually-hidden" aria-live="polite">Loading person</p>
	</div>
{:else if view.status === 'not-found'}
	<div class="panel">
		<h1>Person not found</h1>
		<p class="muted">They may have been deleted.</p>
	</div>
{:else if view.status === 'error'}
	<div class="alert" role="alert">
		<p>{view.message}</p>
		<button class="button" type="button" onclick={() => attempt++}>Retry</button>
	</div>
{:else}
	{@const person = view.person}
	<h1>Edit {person.name}</h1>
	<!-- Keyed on the version, so reloading after a conflict resets the form to the latest values. -->
	{#key person.version}
		<PersonForm
			initial={person}
			submitLabel="Save changes"
			cancelHref={resolve('/people/[id]', { id: person.id })}
			onSubmit={(input) => save(person, input)}
			onReload={() => attempt++}
		/>
	{/key}
{/if}

<style>
	.skeleton.heading {
		height: 36px;
		width: 50%;
		margin-bottom: 1rem;
	}

	.skeleton.form {
		height: 320px;
		max-width: 640px;
		border-radius: var(--radius);
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
