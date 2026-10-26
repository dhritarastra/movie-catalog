<script lang="ts">
	import { resolve } from '$app/paths';
	import { goto } from '$app/navigation';
	import { parseGenres } from '#lib/format.ts';
	import { describeError, GraphQlRequestError, request } from '#lib/graphql/client.ts';
	import { CreateMovieMutation } from '#lib/graphql/operations.ts';
	import { showToast } from '#lib/toasts.svelte.ts';

	let title = $state('');
	let releaseDate = $state('');
	// A number input binds as a number, or null/undefined when empty; 0 is a value, not "empty".
	let runtime = $state<number | null | undefined>(null);
	let genres = $state('');
	let synopsis = $state('');

	let saving = $state(false);
	let errors = $state<Record<string, string>>({});
	let formError = $state<string | null>(null);

	/** Mirrors the catalog's rules for instant feedback; the catalog still has the final say. */
	function validate(): Record<string, string> {
		const found: Record<string, string> = {};
		if (!title.trim()) found.title = 'Enter a title.';
		else if (title.trim().length > 200) found.title = 'Use at most 200 characters.';
		if (runtime != null && (!Number.isInteger(runtime) || runtime < 1 || runtime > 1000)) {
			found.runtimeMinutes = 'Enter whole minutes between 1 and 1000.';
		}
		if (parseGenres(genres).length > 10) found.genres = 'Use at most 10 genres.';
		if (synopsis.trim().length > 2000) found.synopsis = 'Use at most 2000 characters.';
		return found;
	}

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		if (saving) return;
		errors = validate();
		formError = null;
		if (Object.keys(errors).length > 0) {
			document.getElementById(Object.keys(errors)[0])?.focus();
			return;
		}
		saving = true;
		try {
			const { createMovie } = await request(CreateMovieMutation, {
				input: {
					title,
					releaseDate: releaseDate || null,
					runtimeMinutes: runtime ?? null,
					genres: parseGenres(genres),
					synopsis: synopsis || null
				}
			});
			showToast('success', `“${title.trim()}” was added. You can upload artwork now.`);
			await goto(resolve('/movies/[id]', { id: createMovie.id }));
		} catch (error) {
			if (error instanceof GraphQlRequestError && error.fieldErrors.length > 0) {
				errors = error.fieldMessages();
			} else {
				formError = describeError(error);
			}
		} finally {
			saving = false;
		}
	}
</script>

<svelte:head>
	<title>Add movie · Movie Catalog</title>
</svelte:head>

<h1>Add movie</h1>

<form class="panel" onsubmit={submit} novalidate aria-busy={saving}>
	{#if formError}
		<p class="alert" role="alert">{formError}</p>
	{/if}

	<div class="field">
		<label for="title">Title <span aria-hidden="true">*</span></label>
		<input
			id="title"
			bind:value={title}
			required
			maxlength="200"
			aria-invalid={!!errors.title}
			aria-describedby={errors.title ? 'title-error' : undefined}
		/>
		{#if errors.title}<p class="error" id="title-error">{errors.title}</p>{/if}
	</div>

	<div class="row">
		<div class="field">
			<label for="releaseDate">Release date</label>
			<input
				id="releaseDate"
				type="date"
				bind:value={releaseDate}
				min="1870-01-01"
				max="2100-12-31"
				aria-invalid={!!errors.releaseDate}
				aria-describedby={errors.releaseDate ? 'releaseDate-error' : undefined}
			/>
			{#if errors.releaseDate}<p class="error" id="releaseDate-error">{errors.releaseDate}</p>{/if}
		</div>

		<div class="field">
			<label for="runtimeMinutes">Runtime (minutes)</label>
			<input
				id="runtimeMinutes"
				type="number"
				inputmode="numeric"
				min="1"
				max="1000"
				bind:value={runtime}
				aria-invalid={!!errors.runtimeMinutes}
				aria-describedby={errors.runtimeMinutes ? 'runtimeMinutes-error' : undefined}
			/>
			{#if errors.runtimeMinutes}<p class="error" id="runtimeMinutes-error">
					{errors.runtimeMinutes}
				</p>{/if}
		</div>
	</div>

	<div class="field">
		<label for="genres">Genres</label>
		<input
			id="genres"
			bind:value={genres}
			placeholder="Sci-Fi, Drama"
			aria-describedby="genres-hint{errors.genres ? ' genres-error' : ''}"
			aria-invalid={!!errors.genres}
		/>
		<p class="hint muted" id="genres-hint">Separate genres with commas.</p>
		{#if errors.genres}<p class="error" id="genres-error">{errors.genres}</p>{/if}
	</div>

	<div class="field">
		<label for="synopsis">Synopsis</label>
		<textarea
			id="synopsis"
			rows="5"
			maxlength="2000"
			bind:value={synopsis}
			aria-invalid={!!errors.synopsis}
			aria-describedby={errors.synopsis ? 'synopsis-error' : undefined}></textarea>
		{#if errors.synopsis}<p class="error" id="synopsis-error">{errors.synopsis}</p>{/if}
	</div>

	<div class="actions">
		<a class="button" href={resolve('/movies')}>Cancel</a>
		<button class="button primary" type="submit" disabled={saving}>
			{saving ? 'Saving…' : 'Add movie'}
		</button>
	</div>
</form>

<style>
	form {
		display: grid;
		gap: 1.25rem;
		max-width: 640px;
	}

	.row {
		display: grid;
		gap: 1.25rem;
	}

	@media (min-width: 560px) {
		.row {
			grid-template-columns: 1fr 1fr;
		}
	}

	.field {
		display: grid;
		gap: 0.35rem;
	}

	label {
		font-weight: 600;
	}

	input,
	textarea {
		width: 100%;
		min-height: 42px;
		padding: 0.5rem 0.75rem;
		border: 1px solid var(--border);
		border-radius: 8px;
		background: var(--surface);
		color: var(--text);
		font: inherit;
	}

	[aria-invalid='true'] {
		border-color: var(--danger);
	}

	.hint,
	.error {
		margin: 0;
		font-size: 0.875rem;
	}

	.error {
		color: var(--danger);
	}

	.actions {
		display: flex;
		justify-content: flex-end;
		gap: 0.75rem;
	}
</style>
