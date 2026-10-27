<script lang="ts">
	import { resolve } from '$app/paths';
	import '../app.css';
	import favicon from '#lib/assets/favicon.svg';
	import Toasts from '#lib/components/Toasts.svelte';
	import { page } from '$app/state';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	// A section's link is the current page on its list, and marks the current section below it.
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
		<!-- Each section's own "Add" button sits on its list page, leaving room here for search. -->
		<nav aria-label="Main">
			<a href={resolve('/movies')} aria-current={current('/movies')}>Movies</a>
			<a href={resolve('/people')} aria-current={current('/people')}>People</a>
		</nav>
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
		gap: 1rem;
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
	}

	nav a:not(.button) {
		color: var(--text-muted);
		text-decoration: none;
		font-weight: 600;
	}

	nav a[aria-current] {
		color: var(--text);
	}
</style>
