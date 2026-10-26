<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import ArtworkUpload from '#lib/components/ArtworkUpload.svelte';
	import { formatRuntime, releaseYear } from '#lib/format.ts';
	import { describeError, request } from '#lib/graphql/client.ts';
	import type { MovieDetailFieldsFragment } from '#lib/graphql/generated/graphql.ts';
	import { MovieQuery } from '#lib/graphql/operations.ts';
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
				if (error?.name !== 'AbortError') view = { status: 'error', message: describeError(error) };
			});
		return () => controller.abort();
	});

	function uploaded(movie: MovieDetailFieldsFragment) {
		view = { status: 'ready', movie };
		showToast('success', 'Artwork updated.');
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
		<div class="artwork">
			{#if movie.artworkUrl}
				<img src={movie.artworkUrl} alt="Artwork for {movie.title}" />
			{:else}
				<div class="placeholder">
					<span aria-hidden="true">{movie.title.slice(0, 1).toUpperCase()}</span><span class="muted"
						>No artwork yet</span
					>
				</div>
			{/if}
		</div>

		<div class="details">
			<h1>{movie.title}</h1>
			<p class="muted facts">
				{[releaseYear(movie.releaseDate), formatRuntime(movie.runtimeMinutes)]
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
