<script lang="ts">
	import { untrack } from 'svelte';
	import { parseGenres } from '#lib/format.ts';
	import { describeError, GraphQlRequestError } from '#lib/graphql/client.ts';
	import type { ResolvedPathname } from '$app/types';
	import type { MovieInput } from '#lib/graphql/generated/graphql.ts';

	export interface MovieFormValues {
		title: string;
		releaseDate: string | null;
		runtimeMinutes: number | null;
		genres: string[];
		synopsis: string | null;
	}

	interface Props {
		/** Starting values: empty when adding, the stored movie when editing. */
		initial?: MovieFormValues;
		submitLabel: string;
		cancelHref: ResolvedPathname;
		/** Saves the input; a rejection is shown as field errors or a message. */
		onSubmit(input: MovieInput): Promise<void>;
		/** Offered after a conflict, so the user can load the latest version (losing these edits). */
		onReload?(): void;
	}

	let { initial, submitLabel, cancelHref, onSubmit, onReload }: Props = $props();

	// Copied once, deliberately: the form owns its values after it is created, so a parent
	// re-render cannot wipe what the user typed. Edit pages remount it to reset.
	const start = untrack(() => initial);
	let title = $state(start?.title ?? '');
	let releaseDate = $state(start?.releaseDate ?? '');
	// A number input binds as a number, or null/undefined when empty; 0 is a value, not "empty".
	let runtime = $state<number | null | undefined>(start?.runtimeMinutes ?? null);
	let genres = $state(start?.genres.join(', ') ?? '');
	let synopsis = $state(start?.synopsis ?? '');

	let saving = $state(false);
	let errors = $state<Record<string, string>>({});
	let formError = $state<{ message: string; conflict: boolean } | null>(null);

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
			// Every editable field is sent: updates replace the whole record (DECISIONS D5).
			await onSubmit({
				title,
				releaseDate: releaseDate || null,
				runtimeMinutes: runtime ?? null,
				genres: parseGenres(genres),
				synopsis: synopsis || null
			});
		} catch (error) {
			if (error instanceof GraphQlRequestError && error.fieldErrors.length > 0) {
				errors = error.fieldMessages();
			} else {
				const conflict = error instanceof GraphQlRequestError && error.code === 'CONFLICT';
				formError = {
					message: conflict
						? 'Someone else changed this movie while you were editing. Reload to see the latest version; your changes here will be lost.'
						: describeError(error),
					conflict
				};
			}
		} finally {
			saving = false;
		}
	}
</script>

<form class="panel" onsubmit={submit} novalidate aria-busy={saving}>
	{#if formError}
		<div class="alert" role="alert">
			<p>{formError.message}</p>
			{#if formError.conflict && onReload}
				<button class="button" type="button" onclick={onReload}>Reload movie</button>
			{/if}
		</div>
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
		<a class="button" href={cancelHref}>Cancel</a>
		<button class="button primary" type="submit" disabled={saving}>
			{saving ? 'Saving…' : submitLabel}
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

	.alert {
		display: grid;
		gap: 0.5rem;
		justify-items: start;
	}

	.alert p {
		margin: 0;
	}

	.actions {
		display: flex;
		justify-content: flex-end;
		gap: 0.75rem;
	}
</style>
