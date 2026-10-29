<script lang="ts">
	import { resolve } from '$app/paths';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import MovieForm from '#lib/components/MovieForm.svelte';
	import { describeReadError, request } from '#lib/graphql/client.ts';
	import type { MovieDetailFieldsFragment, MovieInput } from '#lib/graphql/generated/graphql.ts';
	import { MovieQuery, UpdateMovieMutation } from '#lib/graphql/operations.ts';
	import { trackVisit } from '#lib/navigation.ts';
	import { showToast } from '#lib/toasts.svelte.ts';

	type View =
		| { status: 'loading' }
		| { status: 'not-found' }
		| { status: 'error'; message: string }
		| { status: 'ready'; movie: MovieDetailFieldsFragment };

	const id = $derived(page.params.id ?? '');
	let view = $state<View>({ status: 'loading' });
	let attempt = $state(0);

	$effect(() => {
		const movieId = id;
		void attempt;
		const controller = new AbortController();
		view = { status: 'loading' };
		request(MovieQuery, { id: movieId }, { signal: controller.signal })
			.then(
				(data) =>
					(view = data.movie ? { status: 'ready', movie: data.movie } : { status: 'not-found' })
			)
			.catch((error) => {
				if (error?.name !== 'AbortError')
					view = { status: 'error', message: describeReadError(error) };
			});
		return () => controller.abort();
	});

	const visit = trackVisit();

	/**
	 * Saves against the version that was loaded, so a newer edit made elsewhere is never
	 * overwritten. If the user leaves while it saves, the save still completes; they get the
	 * toast but are not pulled back to this movie.
	 */
	async function save(movie: MovieDetailFieldsFragment, input: MovieInput) {
		const stillHere = visit.mark();
		await request(UpdateMovieMutation, { id: movie.id, version: movie.version, input });
		showToast('success', 'Changes saved.');
		if (stillHere()) await goto(resolve('/movies/[id]', { id: movie.id }));
	}
</script>

<svelte:head>
	<title
		>{view.status === 'ready' ? `Edit ${view.movie.title}` : 'Edit movie'} · Movie Catalog</title
	>
</svelte:head>

<p><a href={resolve('/movies/[id]', { id })}>← Back to movie</a></p>

{#if view.status === 'loading'}
	<div aria-busy="true">
		<div class="skeleton heading"></div>
		<div class="skeleton form"></div>
		<p class="visually-hidden" aria-live="polite">Loading movie</p>
	</div>
{:else if view.status === 'not-found'}
	<div class="panel">
		<h1>Movie not found</h1>
		<p class="muted">It may have been deleted.</p>
	</div>
{:else if view.status === 'error'}
	<div class="alert" role="alert">
		<p>{view.message}</p>
		<button class="button" type="button" onclick={() => attempt++}>Retry</button>
	</div>
{:else}
	{@const movie = view.movie}
	<h1>Edit {movie.title}</h1>
	<!-- Keyed on the version, so reloading after a conflict resets the form to the latest values. -->
	{#key movie.version}
		<MovieForm
			initial={movie}
			submitLabel="Save changes"
			cancelHref={resolve('/movies/[id]', { id: movie.id })}
			onSubmit={(input) => save(movie, input)}
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
		height: 420px;
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
