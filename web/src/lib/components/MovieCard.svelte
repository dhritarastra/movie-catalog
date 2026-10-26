<script lang="ts">
	import { resolve } from '$app/paths';
	import { releaseYear } from '#lib/format.ts';
	import type { MovieCardFieldsFragment } from '#lib/graphql/generated/graphql.ts';

	let { movie }: { movie: MovieCardFieldsFragment } = $props();

	const year = $derived(releaseYear(movie.releaseDate));
</script>

<a class="card" href={resolve('/movies/[id]', { id: movie.id })}>
	<div class="poster">
		{#if movie.thumbnailUrl}
			<img src={movie.thumbnailUrl} alt="" loading="lazy" />
		{:else}
			<span class="placeholder" aria-hidden="true">{movie.title.slice(0, 1).toUpperCase()}</span>
		{/if}
	</div>
	<div class="body">
		<h2>{movie.title}</h2>
		<p class="muted">
			{[year, movie.genres.slice(0, 2).join(', ')].filter(Boolean).join(' · ') || 'No details yet'}
		</p>
	</div>
</a>

<style>
	.card {
		display: flex;
		flex-direction: column;
		height: 100%;
		overflow: hidden;
		border: 1px solid var(--border);
		border-radius: var(--radius);
		background: var(--surface);
		color: inherit;
		text-decoration: none;
		box-shadow: var(--shadow);
		transition: transform 0.15s ease;
	}

	.card:hover {
		transform: translateY(-2px);
	}

	.poster {
		display: grid;
		place-items: center;
		aspect-ratio: 2 / 3;
		background: var(--surface-muted);
	}

	img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.placeholder {
		font-size: 3rem;
		font-weight: 800;
		color: var(--text-muted);
	}

	.body {
		padding: 0.75rem;
	}

	h2 {
		margin: 0;
		font-size: 1rem;
		line-height: 1.3;
	}

	p {
		margin: 0.25rem 0 0;
		font-size: 0.875rem;
	}

	@media (prefers-reduced-motion: reduce) {
		.card {
			transition: none;
		}
	}
</style>
