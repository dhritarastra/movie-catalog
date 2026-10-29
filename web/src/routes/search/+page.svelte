<script lang="ts">
	import { onMount, untrack } from 'svelte';
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import type { ResolvedPathname } from '$app/types';
	import { yearOf } from '#lib/format.ts';
	import { describeError, request } from '#lib/graphql/client.ts';
	import type { SearchQuery as SearchResult } from '#lib/graphql/generated/graphql.ts';
	import { SearchQuery } from '#lib/graphql/operations.ts';
	import { roleSummary, SEARCH_LIMIT, searchQueryString } from '#lib/search.ts';

	const SEARCH_DELAY_MS = 250;

	type View =
		| { status: 'idle' }
		| { status: 'error'; message: string }
		| { status: 'ready'; text: string; hits: SearchResult['search'] };

	const urlQuery = $derived(page.url.searchParams.get('q') ?? '');
	let query = $state(untrack(() => urlQuery));
	// Derived values only notify when they change, so a trailing space does not search again.
	const searchText = $derived(query.trim());
	let view = $state<View>({ status: 'idle' });
	let searching = $state(false);
	let attempt = $state(0);
	let input: HTMLInputElement | undefined = $state();

	onMount(() => input?.focus());

	// The address changes without this box when a header search is sent from here, or on
	// Back and Forward; the box follows it.
	$effect(() => {
		const fromUrl = urlQuery;
		untrack(() => {
			if (fromUrl.trim() !== query.trim()) query = fromUrl;
		});
	});

	// Searches after a pause in typing. The cleanup cancels both the wait and the request it
	// started, so an older, slower answer can never replace a newer one.
	$effect(() => {
		const text = searchText;
		void attempt;
		if (!text) {
			view = { status: 'idle' };
			searching = false;
			showInAddress('');
			return;
		}
		const controller = new AbortController();
		const timer = setTimeout(
			() => {
				showInAddress(text);
				searching = true;
				request(SearchQuery, { query: text, limit: SEARCH_LIMIT }, { signal: controller.signal })
					.then((data) => (view = { status: 'ready', text, hits: data.search }))
					.catch((error) => {
						if (error?.name !== 'AbortError')
							view = { status: 'error', message: describeError(error) };
					})
					.finally(() => {
						if (!controller.signal.aborted) searching = false;
					});
			},
			// A search arriving in the address (a link, Back, the header box) runs at once.
			text === urlQuery.trim() ? 0 : SEARCH_DELAY_MS
		);
		return () => {
			clearTimeout(timer);
			controller.abort();
		};
	});

	/** Keeps the address in step with the box, replacing the entry so typing is not history. */
	function showInAddress(text: string) {
		if (text === urlQuery.trim()) return;
		void goto(`${resolve('/search')}${searchQueryString(text)}` as ResolvedPathname, {
			replace: true,
			// Leaves focus in the box and the scroll position alone while typing.
			reset: false
		});
	}
</script>

<svelte:head>
	<title>{searchText ? `“${searchText}”` : 'Search'} · Movie Catalog</title>
</svelte:head>

<h1>Search</h1>

<form class="search" role="search" onsubmit={(event) => event.preventDefault()}>
	<label for="search-query">Search movies and people</label>
	<input
		id="search-query"
		type="search"
		bind:this={input}
		bind:value={query}
		maxlength="200"
		autocomplete="off"
		placeholder="Part of a title or a name"
		aria-describedby="search-status"
	/>
</form>

<!-- Everything a search changes is announced here, so the results are heard as well as seen. -->
<p class="muted" id="search-status" aria-live="polite">
	{#if searching}
		Searching…
	{:else if view.status === 'idle'}
		Type part of a movie title or a person's name.
	{:else if view.status === 'ready' && view.hits.length === 0}
		Nothing matches “{view.text}”.
	{:else if view.status === 'ready'}
		{view.hits.length}
		{view.hits.length === 1 ? 'result' : 'results'} for “{view.text}”.{view.hits.length >=
		SEARCH_LIMIT
			? ' Showing the best matches; type more to narrow them down.'
			: ''}
	{/if}
</p>

{#if view.status === 'error'}
	<div class="alert" role="alert">
		<p>{view.message}</p>
		<button class="button" type="button" onclick={() => attempt++}>Retry</button>
	</div>
{:else if view.status === 'ready' && view.hits.length > 0}
	<ul class="results" aria-label="Results">
		{#each view.hits as hit (hit.__typename + hit.id)}
			<li>
				{#if hit.__typename === 'Movie'}
					<span class="kind">Movie</span>
					<a href={resolve('/movies/[id]', { id: hit.id })}>{hit.title}</a>
					{#if hit.releaseDate}<span class="muted">{yearOf(hit.releaseDate)}</span>{/if}
				{:else}
					<span class="kind">Person</span>
					<a href={resolve('/people/[id]', { id: hit.id })}>{hit.name}</a>
					<span class="muted">{roleSummary(hit.credits) ?? 'No credits yet'}</span>
				{/if}
			</li>
		{/each}
	</ul>
{/if}

<style>
	h1 {
		margin-top: 0;
	}

	.search {
		display: grid;
		gap: 0.35rem;
		max-width: 640px;
	}

	.search label {
		font-weight: 600;
	}

	.search input {
		width: 100%;
		min-height: 42px;
		padding: 0.5rem 0.75rem;
		border: 1px solid var(--border);
		border-radius: 8px;
		background: var(--surface);
		color: var(--text);
		font: inherit;
	}

	.results {
		display: grid;
		gap: 0.25rem;
		max-width: 720px;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.results li {
		display: flex;
		align-items: baseline;
		gap: 0.5rem;
		flex-wrap: wrap;
		padding: 0.5rem 0;
		border-bottom: 1px solid var(--border);
		overflow-wrap: anywhere;
	}

	.kind {
		padding: 0.1rem 0.5rem;
		border-radius: 999px;
		background: var(--surface-muted);
		font-size: 0.8rem;
		font-weight: 600;
	}

	.alert {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
		flex-wrap: wrap;
		max-width: 720px;
	}

	.alert p {
		margin: 0;
	}
</style>
