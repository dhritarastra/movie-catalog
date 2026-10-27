<script lang="ts">
	import { tick, untrack } from 'svelte';
	import { parseGenres } from '#lib/format.ts';
	import { describeError } from '#lib/graphql/client.ts';
	import { trackVisit } from '#lib/navigation.ts';
	import { showToast } from '#lib/toasts.svelte.ts';
	import { saveFailure, type SaveFailure } from '#lib/forms.ts';
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

	// The input ids, which are also the field names in the gateway's errors.
	const FIELDS = ['title', 'releaseDate', 'runtimeMinutes', 'genres', 'synopsis'];

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
	let alert: HTMLElement | undefined = $state();
	let form: HTMLFormElement | undefined = $state();
	const visit = trackVisit();

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
			form?.querySelector<HTMLElement>(`#${Object.keys(errors)[0]}`)?.focus();
			return;
		}
		// The fields are locked until the save finishes, so what was sent is what the user sees.
		saving = true;
		const stillHere = visit.mark();
		const subject = title.trim();
		let failure: SaveFailure | null = null;
		try {
			// Every editable field is sent: updates replace the whole record.
			await onSubmit({
				title,
				releaseDate: releaseDate || null,
				runtimeMinutes: runtime ?? null,
				genres: parseGenres(genres),
				synopsis: synopsis || null
			});
		} catch (error) {
			if (!stillHere()) {
				showToast('error', `Save for “${subject}”: ${describeError(error)}`);
				return;
			}
			failure = saveFailure(
				error,
				FIELDS,
				'Someone else changed this movie while you were editing. Reload to see the latest version; your changes here will be lost.'
			);
		} finally {
			if (stillHere()) saving = false;
		}
		if (failure && stillHere()) await showFailure(failure, stillHere);
	}

	/** Locking the fields took focus off the form, so focus goes back to what needs attention. */
	async function showFailure(failure: SaveFailure, stillHere: () => boolean) {
		if (failure.kind === 'fields') errors = failure.errors;
		else formError = failure;
		await tick();
		if (!stillHere()) return;
		if (failure.kind === 'fields')
			form?.querySelector<HTMLElement>(`#${Object.keys(errors)[0]}`)?.focus();
		else alert?.focus();
	}
</script>

<form bind:this={form} class="panel form" onsubmit={submit} novalidate aria-busy={saving}>
	{#if formError}
		<div class="alert" role="alert" tabindex="-1" bind:this={alert}>
			<p>{formError.message}</p>
			{#if formError.conflict && onReload}
				<button class="button" type="button" onclick={onReload}>Reload movie</button>
			{/if}
		</div>
	{/if}

	<fieldset disabled={saving}>
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
				{#if errors.releaseDate}<p class="error" id="releaseDate-error">
						{errors.releaseDate}
					</p>{/if}
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
	</fieldset>

	<div class="actions">
		<a class="button" href={cancelHref}>Cancel</a>
		<button class="button primary" type="submit" disabled={saving}>
			{saving ? 'Saving…' : submitLabel}
		</button>
	</div>
</form>
