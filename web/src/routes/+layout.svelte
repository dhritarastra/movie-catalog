<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import type { ResolvedPathname } from '$app/types';
	import '../app.css';
	import { searchQueryString } from '#lib/search.ts';
	import favicon from '#lib/assets/favicon.svg';
	import Toasts from '#lib/components/Toasts.svelte';
	import { page } from '$app/state';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const onSearchPage = $derived(page.url.pathname === resolve('/search'));
	let headerQuery = $state('');

	function search(event: SubmitEvent) {
		event.preventDefault();
		const text = headerQuery;
		headerQuery = '';
		void goto(`${resolve('/search')}${searchQueryString(text)}` as ResolvedPathname);
	}

	function current(path: '/movies' | '/people'): 'page' | 'true' | undefined {
		if (page.url.pathname === path) return 'page';
		return page.url.pathname.startsWith(`${path}/`) ? 'true' : undefined;
	}
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<title>Movie Catalog</title>
</svelte:head>

<a class="skip-link" href="#main">Skip to content</a>

<header class="site-header">
	<div class="container header-inner">
		<a class="brand" href={resolve('/movies')}>Movie Catalog</a>
		<nav aria-label="Main">
			<a href={resolve('/movies')} aria-current={current('/movies')}>Movies</a>
			<a href={resolve('/people')} aria-current={current('/people')}>People</a>
			{#if onSearchPage}
				<a href={resolve('/search')} aria-current="page">Search</a>
			{/if}
		</nav>
		{#if !onSearchPage}
			<form class="header-search" role="search" onsubmit={search}>
				<label class="visually-hidden" for="header-search">Search movies and people</label>
				<input
					id="header-search"
					type="search"
					bind:value={headerQuery}
					maxlength="200"
					autocomplete="off"
					placeholder="Search…"
				/>
			</form>
		{/if}
	</div>
</header>

<main id="main" class="container">
	{@render children()}
</main>

<Toasts />

<style>
	.skip-link {
		position: absolute;
		left: 8px;
		top: -48px;
		padding: 0.5rem 1rem;
		background: var(--surface);
		border-radius: 8px;
		z-index: 10;
	}

	.skip-link:focus {
		top: 8px;
	}

	.site-header {
		background: var(--surface);
		border-bottom: 1px solid var(--border);
	}

	.header-inner {
		display: flex;
		align-items: center;
		justify-content: space-between;
		flex-wrap: wrap;
		gap: 0.5rem 1rem;
		min-height: 64px;
	}

	.brand {
		font-weight: 800;
		font-size: 1.1rem;
		color: var(--text);
		text-decoration: none;
	}

	nav {
		display: flex;
		align-items: center;
		gap: 1rem;
		margin-left: auto;
	}

	nav a:not(.button) {
		color: var(--text-muted);
		text-decoration: none;
		font-weight: 600;
	}

	nav a[aria-current] {
		color: var(--text);
	}

	.header-search input {
		width: 11rem;
		min-height: 36px;
		padding: 0.3rem 0.6rem;
		border: 1px solid var(--border);
		border-radius: 8px;
		background: var(--surface);
		color: var(--text);
		font: inherit;
	}

	@media (max-width: 600px) {
		.header-search {
			flex: 1 1 100%;
			padding-bottom: 0.75rem;
		}

		.header-search input {
			width: 100%;
		}
	}
</style>
