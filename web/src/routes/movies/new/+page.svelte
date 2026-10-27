<script lang="ts">
	import { resolve } from '$app/paths';
	import { goto } from '$app/navigation';
	import MovieForm from '#lib/components/MovieForm.svelte';
	import { request } from '#lib/graphql/client.ts';
	import type { MovieInput } from '#lib/graphql/generated/graphql.ts';
	import { CreateMovieMutation } from '#lib/graphql/operations.ts';
	import { trackVisit } from '#lib/navigation.ts';
	import { showToast } from '#lib/toasts.svelte.ts';

	const visit = trackVisit();

	async function create(input: MovieInput) {
		const stillHere = visit.mark();
		const { createMovie } = await request(CreateMovieMutation, { input });
		if (stillHere()) {
			showToast('success', `“${input.title.trim()}” was added. You can upload artwork now.`);
			await goto(resolve('/movies/[id]', { id: createMovie.id }));
		} else {
			showToast('success', `“${input.title.trim()}” was added.`);
		}
	}
</script>

<svelte:head>
	<title>Add movie · Movie Catalog</title>
</svelte:head>

<h1>Add movie</h1>

<MovieForm submitLabel="Add movie" cancelHref={resolve('/movies')} onSubmit={create} />
