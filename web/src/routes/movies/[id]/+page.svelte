<script lang="ts">
	import { resolve } from '$app/paths';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import ArtworkUpload from '#lib/components/ArtworkUpload.svelte';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import { formatRuntime, yearOf } from '#lib/format.ts';
	import { describeError, GraphQlRequestError, request } from '#lib/graphql/client.ts';
	import type { MovieDetailFieldsFragment } from '#lib/graphql/generated/graphql.ts';
	import {
		DeleteMovieMutation,
		MovieQuery,
		RemoveArtworkMutation
	} from '#lib/graphql/operations.ts';
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
		// Back/Forward can reuse this component for another ID while an action is pending.
		confirming = null;
		busy = false;
		confirmError = null;
		view = { status: 'loading' };
		request(MovieQuery, { id: movieId }, { signal: controller.signal })
			.then(
				(data) =>
					(view = data.movie ? { status: 'ready', movie: data.movie } : { status: 'not-found' })
			)
			.catch((error) => {
				if (error?.name !== 'AbortError') view = { status: 'error', message: describeError(error) };
			});
		return () => controller.abort();
	});

	// Shown only if this page still shows that movie; the toast confirms the upload either way.
	function uploaded(movie: MovieDetailFieldsFragment) {
		if (movie.id === id) view = { status: 'ready', movie };
		showToast('success', 'Artwork updated.');
	}

	const visit = trackVisit();

	// One confirmation dialog, for whichever destructive action was chosen.
	let confirming = $state<'delete' | 'remove-artwork' | null>(null);
	let busy = $state(false);
	let confirmError = $state<string | null>(null);

	function askToConfirm(action: 'delete' | 'remove-artwork') {
		confirmError = null;
		confirming = action;
	}

	/**
	 * The dialog blocks the page while this runs, but not the browser's Back button. If the user
	 * leaves, the outcome is still confirmed with a toast, but this page neither navigates nor
	 * updates (see trackVisit).
	 */
	async function confirm(movie: MovieDetailFieldsFragment) {
		const stillHere = visit.mark();
		busy = true;
		confirmError = null;
		try {
			if (confirming === 'delete') {
				await request(DeleteMovieMutation, { id: movie.id, version: movie.version });
				if (stillHere()) confirming = null;
				showToast('success', `“${movie.title}” was deleted.`);
				if (stillHere()) await goto(resolve('/movies'));
			} else {
				const { removeArtwork } = await request(RemoveArtworkMutation, {
					movieId: movie.id,
					version: movie.version
				});
				if (stillHere()) confirming = null;
				if (stillHere()) view = { status: 'ready', movie: removeArtwork };
				showToast('success', 'Artwork removed.');
			}
		} catch (error) {
			if (!stillHere()) {
				showToast('error', `Movie action for “${movie.title}”: ${describeError(error)}`);
				return;
			}
			if (error instanceof GraphQlRequestError && error.code === 'CONFLICT') {
				confirming = null;
				// Nothing was changed. Show the latest version so the user decides again knowingly.
				attempt++;
				showToast(
					'error',
					'Someone else changed this movie, so nothing was done. This is the latest version; try again if you still want to.'
				);
			} else {
				confirmError = describeError(error);
			}
		} finally {
			if (stillHere()) busy = false;
		}
	}
</script>

<svelte:head>
	<title>{view.status === 'ready' ? view.movie.title : 'Movie'} · Movie Catalog</title>
</svelte:head>

<p><a href={resolve('/movies')}>← All movies</a></p>

{#if view.status === 'loading'}
	<div class="layout" aria-busy="true">
		<div class="skeleton artwork"></div>
		<div class="details">
			<div class="skeleton line wide"></div>
			<div class="skeleton line"></div>
			<div class="skeleton line"></div>
		</div>
		<p class="visually-hidden" aria-live="polite">Loading movie</p>
	</div>
{:else if view.status === 'not-found'}
	<div class="panel">
		<h1>Movie not found</h1>
		<p class="muted">It may have been deleted, or the link is wrong.</p>
	</div>
{:else if view.status === 'error'}
	<div class="alert" role="alert">
		<p>{view.message}</p>
		<button class="button" type="button" onclick={() => attempt++}>Retry</button>
	</div>
{:else}
	{@const movie = view.movie}
	<article class="layout">
		<div class="artwork-column">
			<div class="artwork">
				{#if movie.artworkUrl}
					<img src={movie.artworkUrl} alt="Artwork for {movie.title}" />
				{:else}
					<div class="placeholder">
						<span aria-hidden="true">{movie.title.slice(0, 1).toUpperCase()}</span><span
							class="muted">No artwork yet</span
						>
					</div>
				{/if}
			</div>
			{#if movie.artworkUrl}
				<button class="button link" type="button" onclick={() => askToConfirm('remove-artwork')}>
					Remove artwork
				</button>
			{/if}
		</div>

		<div class="details">
			<div class="title-row">
				<h1>{movie.title}</h1>
				<div class="actions">
					<a class="button" href={resolve('/movies/[id]/edit', { id: movie.id })}>Edit</a>
					<button class="button" type="button" onclick={() => askToConfirm('delete')}>Delete</button
					>
				</div>
			</div>
			<p class="muted facts">
				{[yearOf(movie.releaseDate), formatRuntime(movie.runtimeMinutes)]
					.filter(Boolean)
					.join(' · ')}
			</p>
			{#if movie.genres.length > 0}
				<ul class="genres" aria-label="Genres">
					{#each movie.genres as genre (genre)}<li>{genre}</li>{/each}
				</ul>
			{/if}
			{#if movie.synopsis}<p class="synopsis">{movie.synopsis}</p>{/if}

			<ArtworkUpload
				movieId={movie.id}
				version={movie.version}
				hasArtwork={!!movie.artworkUrl}
				onUploaded={uploaded}
				onReload={() => attempt++}
			/>
		</div>
	</article>

	<ConfirmDialog
		open={confirming !== null}
		title={confirming === 'delete' ? `Delete “${movie.title}”?` : 'Remove the artwork?'}
		message={confirming === 'delete'
			? 'The movie and its artwork will be permanently deleted.'
			: 'The image will be deleted. You can upload a new one afterwards.'}
		confirmLabel={confirming === 'delete' ? 'Delete movie' : 'Remove artwork'}
		{busy}
		error={confirmError}
		onConfirm={() => confirm(movie)}
		onCancel={() => !busy && (confirming = null)}
	/>
{/if}

<style>
	.layout {
		display: grid;
		gap: 24px;
	}

	@media (min-width: 768px) {
		.layout {
			grid-template-columns: minmax(220px, 320px) 1fr;
			align-items: start;
		}
	}

	.artwork {
		aspect-ratio: 2 / 3;
		border-radius: var(--radius);
		overflow: hidden;
		background: var(--surface-muted);
		max-width: 360px;
	}

	.artwork img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.placeholder {
		display: grid;
		place-content: center;
		justify-items: center;
		height: 100%;
		gap: 0.5rem;
	}

	.placeholder span:first-child {
		font-size: 4rem;
		font-weight: 800;
		color: var(--text-muted);
	}

	.details {
		display: grid;
		gap: 0.75rem;
		align-content: start;
	}

	.details h1 {
		margin: 0;
	}

	.artwork-column {
		display: grid;
		gap: 0.5rem;
		justify-items: start;
	}

	.title-row {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 1rem;
		flex-wrap: wrap;
	}

	.title-row .actions {
		display: flex;
		gap: 0.5rem;
	}

	.facts {
		margin: 0;
	}

	.genres {
		display: flex;
		flex-wrap: wrap;
		gap: 0.5rem;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.genres li {
		padding: 0.15rem 0.6rem;
		border-radius: 999px;
		background: var(--surface-muted);
		font-size: 0.875rem;
	}

	.synopsis {
		margin: 0;
		max-width: 65ch;
	}

	.skeleton.line {
		height: 18px;
		width: 50%;
	}

	.skeleton.line.wide {
		height: 32px;
		width: 70%;
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
