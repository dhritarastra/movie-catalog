<script lang="ts">
	import { resolve } from '$app/paths';
	import { goto } from '$app/navigation';
	import PersonForm from '#lib/components/PersonForm.svelte';
	import { request } from '#lib/graphql/client.ts';
	import type { PersonInput } from '#lib/graphql/generated/graphql.ts';
	import { CreatePersonMutation } from '#lib/graphql/operations.ts';
	import { trackVisit } from '#lib/navigation.ts';
	import { showToast } from '#lib/toasts.svelte.ts';

	const visit = trackVisit();

	async function create(input: PersonInput) {
		const stillHere = visit.mark();
		const { createPerson } = await request(CreatePersonMutation, { input });
		showToast('success', `${input.name.trim()} was added.`);
		if (stillHere()) await goto(resolve('/people/[id]', { id: createPerson.id }));
	}
</script>

<svelte:head>
	<title>Add person · Movie Catalog</title>
</svelte:head>

<h1>Add person</h1>

<PersonForm submitLabel="Add person" cancelHref={resolve('/people')} onSubmit={create} />
