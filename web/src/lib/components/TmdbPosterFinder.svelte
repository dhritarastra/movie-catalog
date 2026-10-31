<script lang="ts">
	import {
		describeError,
		describeReadError,
		GraphQlRequestError,
		request
	} from '#lib/graphql/client.ts';
	import type {
		FindTmdbPosterQuery as FindTmdbPosterResult,
		MovieDetailFieldsFragment
	} from '#lib/graphql/generated/graphql.ts';
	import { FindTmdbPosterQuery, UseTmdbPosterMutation } from '#lib/graphql/operations.ts';
	import { trackVisit } from '#lib/navigation.ts';
	import { tmdbLabel } from '#lib/tmdb.ts';
	import { showToast } from '#lib/toasts.svelte.ts';

	interface Props {
		movieId: string;
		version: number;
		// True while a chosen file is uploading.
		disabled: boolean;
		onUploaded(movie: MovieDetailFieldsFragment): void;
		onReload(): void;
	}

	let { movieId, version, disabled, onUploaded, onReload }: Props = $props();

	type Poster = NonNullable<FindTmdbPosterResult['tmdbPoster']>;
	type Step =
		| { name: 'idle' }
		| { name: 'searching' }
		| { name: 'found'; poster: Poster }
		| { name: 'none' }
		| { name: 'saving'; poster: Poster }
		| { name: 'error'; message: string; reload: boolean };

	let step = $state<Step>({ name: 'idle' });
	let search: AbortController | null = null;
	const visit = trackVisit();

	$effect(() => () => search?.abort());

	async function find() {
		search?.abort();
		search = new AbortController();
		step = { name: 'searching' };
		try {
			const { tmdbPoster } = await request(
				FindTmdbPosterQuery,
				{ movieId },
				{ signal: search.signal }
			);
			step = tmdbPoster ? { name: 'found', poster: tmdbPoster } : { name: 'none' };
		} catch (error) {
			if ((error as Error)?.name !== 'AbortError') {
				step = { name: 'error', message: describeReadError(error), reload: false };
			}
		}
	}

	async function use(poster: Poster) {
		const stillHere = visit.mark();
		step = { name: 'saving', poster };
		try {
			const { useTmdbPoster } = await request(UseTmdbPosterMutation, {
				movieId,
				version,
				tmdbId: poster.tmdbId
			});
			if (!stillHere()) {
				showToast('success', 'Artwork updated.');
				return;
			}
			step = { name: 'idle' };
			onUploaded(useTmdbPoster);
		} catch (error) {
			if (!stillHere()) {
				showToast('error', `TMDB poster: ${describeError(error)}`);
				return;
			}
			const code = error instanceof GraphQlRequestError ? error.code : null;
			step = {
				name: 'error',
				message: describeError(error),
				reload: code === 'CONFLICT' || code === 'SERVICE_UNAVAILABLE'
			};
		}
	}
</script>

<button
	class="button"
	type="button"
	onclick={find}
	disabled={disabled || step.name === 'searching' || step.name === 'saving'}
>
	{step.name === 'searching' ? 'Searching TMDB…' : 'Find poster on TMDB'}
</button>

{#if step.name !== 'idle'}
	<div class="tmdb" aria-live="polite">
		<p class="muted small">
			We look up this title and release year on The Movie Database (TMDB). It may not have this
			film, or may find a different film with the same name, so check the poster before using it.
		</p>

		{#if step.name === 'found' || step.name === 'saving'}
			{@const poster = step.poster}
			{@const label = tmdbLabel(poster)}
			<figure>
				<img src={poster.previewUrl} alt="Poster from TMDB for {label}" />
				<figcaption class="muted small">Poster from TMDB</figcaption>
			</figure>
			<p>Is this the poster for {label}?</p>
			<div class="choices">
				<button
					class="button primary"
					type="button"
					onclick={() => use(poster)}
					disabled={step.name === 'saving'}
				>
					{step.name === 'saving' ? 'Saving…' : 'Use this poster'}
				</button>
				<button
					class="button"
					type="button"
					onclick={() => (step = { name: 'idle' })}
					disabled={step.name === 'saving'}>Not this one</button
				>
			</div>
		{:else if step.name === 'none'}
			<p>TMDB has no poster for this title. You can still choose an image.</p>
			<button class="button link" type="button" onclick={() => (step = { name: 'idle' })}
				>Close</button
			>
		{:else if step.name === 'error'}
			<div class="alert" role="alert">
				<p>{step.message}</p>
				{#if step.reload}
					<button class="button" type="button" onclick={onReload}>Reload movie</button>
				{:else}
					<button class="button" type="button" onclick={find}>Try again</button>
				{/if}
			</div>
		{/if}
	</div>
{/if}

<style>
	.tmdb {
		flex-basis: 100%;
		display: grid;
		gap: 0.5rem;
		justify-items: start;
	}

	p {
		margin: 0;
	}

	.small {
		font-size: 0.875rem;
	}

	figure {
		display: grid;
		gap: 0.25rem;
		margin: 0;
	}

	img {
		max-width: 160px;
		max-height: 240px;
		border-radius: 8px;
		border: 1px solid var(--border);
		object-fit: contain;
	}

	.choices {
		display: flex;
		gap: 0.75rem;
		flex-wrap: wrap;
	}

	.alert {
		display: grid;
		gap: 0.5rem;
		justify-items: start;
	}

	.button.link {
		color: var(--accent);
	}
</style>
