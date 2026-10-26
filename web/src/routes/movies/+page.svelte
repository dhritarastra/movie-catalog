<script lang="ts">
	import { resolve } from '$app/paths';
	import type { ResolvedPathname } from '$app/types';
	import { page } from '$app/state';
	import MovieCard from '#lib/components/MovieCard.svelte';
	import { describeError, request } from '#lib/graphql/client.ts';
	import type { MoviesQuery as MoviesResult } from '#lib/graphql/generated/graphql.ts';
	import { MoviesQuery } from '#lib/graphql/operations.ts';

	const PAGE_SIZE = 12;

	// resolve() builds the path; the page number is a query string, which resolve() does not take.
	const pageHref = (n: number) => `${resolve('/movies')}?page=${n}` as ResolvedPathname;

	type View =
		| { status: 'loading' }
		| { status: 'error'; message: string }
		| { status: 'ready'; movies: MoviesResult['movies'] };

	const pageNumber = $derived(
		Math.max(1, Math.floor(Number(page.url.searchParams.get('page'))) || 1)
	);
	let view = $state<View>({ status: 'loading' });
	let attempt = $state(0);

	// Reloads whenever the page number changes (or Retry is pressed). Aborting the previous
	// request on change means a slow earlier response can never overwrite a newer page.
	$effect(() => {
		const offset = (pageNumber - 1) * PAGE_SIZE;
		void attempt;
		const controller = new AbortController();
		view = { status: 'loading' };
		request(MoviesQuery, { page: { offset, limit: PAGE_SIZE } }, { signal: controller.signal })
			.then((data) => (view = { status: 'ready', movies: data.movies }))
			.catch((error) => {
				if (error?.name !== 'AbortError') view = { status: 'error', message: describeError(error) };
			});
		return () => controller.abort();
	});

	const lastPage = $derived(
		view.status === 'ready' ? Math.max(1, Math.ceil(view.movies.totalCount / PAGE_SIZE)) : 1
	);
</script>

<svelte:head>
	<title>Movies · Movie Catalog</title>
</svelte:head>

<div class="heading">
	<h1>Movies</h1>
	{#if view.status === 'ready' && view.movies.totalCount > 0}
		<p class="muted">
			{view.movies.offset + 1}–{view.movies.offset + view.movies.items.length} of {view.movies
				.totalCount}
		</p>
	{/if}
</div>

<p class="visually-hidden" aria-live="polite">
	{view.status === 'loading'
		? 'Loading movies'
		: view.status === 'ready'
			? `${view.movies.items.length} movies shown`
			: ''}
</p>

{#if view.status === 'loading'}
	<ul class="grid" aria-busy="true" aria-label="Loading movies">
		{#each { length: 8 }, i (i)}
			<li class="skeleton-card" aria-hidden="true">
				<div class="skeleton poster"></div>
				<div class="skeleton line"></div>
				<div class="skeleton line short"></div>
			</li>
		{/each}
	</ul>
{:else if view.status === 'error'}
	<div class="alert" role="alert">
		<p>{view.message}</p>
		<button class="button" type="button" onclick={() => attempt++}>Retry</button>
	</div>
{:else if view.movies.totalCount === 0}
	<div class="panel empty">
		<h2>No movies yet</h2>
		<p class="muted">Add the first one to start the catalog.</p>
		<a class="button primary" href={resolve('/movies/new')}>Add movie</a>
	</div>
{:else if view.movies.items.length === 0}
	<!-- Happens when movies were removed while you were on a later page. -->
	<div class="panel empty">
		<h2>This page is empty</h2>
		<a class="button" href={pageHref(lastPage)}>Go to the last page</a>
	</div>
{:else}
	<ul class="grid">
		{#each view.movies.items as movie (movie.id)}
			<li><MovieCard {movie} /></li>
		{/each}
	</ul>

	{#if lastPage > 1}
		<nav class="pager" aria-label="Pagination">
			<a
				class="button"
				href={pageHref(pageNumber - 1)}
				aria-disabled={pageNumber <= 1}
				tabindex={pageNumber <= 1 ? -1 : undefined}
				onclick={(e) => pageNumber <= 1 && e.preventDefault()}>Previous</a
			>
			<span class="muted">Page {pageNumber} of {lastPage}</span>
			<a
				class="button"
				href={pageHref(pageNumber + 1)}
				aria-disabled={pageNumber >= lastPage}
				tabindex={pageNumber >= lastPage ? -1 : undefined}
				onclick={(e) => pageNumber >= lastPage && e.preventDefault()}>Next</a
			>
		</nav>
	{/if}
{/if}

<style>
	.heading {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 1rem;
		flex-wrap: wrap;
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
		gap: 16px;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	@media (min-width: 768px) {
		.grid {
			grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
			gap: 20px;
		}
	}

	.skeleton-card {
		display: grid;
		gap: 8px;
	}

	.skeleton.poster {
		aspect-ratio: 2 / 3;
		border-radius: var(--radius);
	}

	.skeleton.line {
		height: 14px;
	}

	.skeleton.line.short {
		width: 60%;
	}

	.empty {
		text-align: center;
		padding-block: 3rem;
	}

	.empty h2 {
		margin-top: 0;
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

	.pager {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 1rem;
		margin-top: 2rem;
	}
</style>
