<script lang="ts">
	import { onMount, untrack } from 'svelte';
	import { afterNavigate, goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import type { ResolvedPathname } from '$app/types';
	import { yearOf } from '#lib/format.ts';
	import { describeReadError, request } from '#lib/graphql/client.ts';
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
	const searchText = $derived(query.trim());
	let view = $state<View>({ status: 'idle' });
	let searching = $state(false);
	let attempt = $state(0);
	let input: HTMLInputElement | undefined = $state();

	onMount(() => input?.focus());

	// Follow Back/Forward, but not our own address updates, which can lag behind typing.
	afterNavigate(({ type, to }) => {
		if (type === 'popstate') query = to?.url.searchParams.get('q') ?? '';
	});

	// Wait for a pause in typing; the cleanup cancels older requests so they never win.
	$effect(() => {
		const text = searchText;
		void attempt;
		const fromAddress = untrack(() => urlQuery.trim());
		if (!text) {
			view = { status: 'idle' };
			searching = false;
			untrack(() => showInAddress(''));
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
							view = { status: 'error', message: describeReadError(error) };
					})
					.finally(() => {
						if (!controller.signal.aborted) searching = false;
					});
			},
			// A search from the address (a link, Back) runs at once.
			text === fromAddress ? 0 : SEARCH_DELAY_MS
		);
		return () => {
			clearTimeout(timer);
			controller.abort();
		};
	});

	function showInAddress(text: string) {
		if (text === urlQuery.trim()) return;
		void goto(`${resolve('/search')}${searchQueryString(text)}` as ResolvedPathname, {
			replace: true,
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
