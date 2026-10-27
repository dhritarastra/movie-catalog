<script lang="ts">
	import { tick, untrack } from 'svelte';
	import { localToday } from '#lib/format.ts';
	import { saveFailure, type SaveFailure } from '#lib/forms.ts';
	import type { ResolvedPathname } from '$app/types';
	import type { PersonInput } from '#lib/graphql/generated/graphql.ts';

	export interface PersonFormValues {
		name: string;
		birthDate: string | null;
		bio: string | null;
	}

	interface Props {
		/** Starting values: empty when adding, the stored person when editing. */
		initial?: PersonFormValues;
		submitLabel: string;
		cancelHref: ResolvedPathname;
		/** Saves the input; a rejection is shown as field errors or a message. */
		onSubmit(input: PersonInput): Promise<void>;
		/** Offered after a conflict, so the user can load the latest version (losing these edits). */
		onReload?(): void;
	}

	let { initial, submitLabel, cancelHref, onSubmit, onReload }: Props = $props();

	const FIELDS = ['name', 'birthDate', 'bio'];
	const EARLIEST_BIRTH = '1800-01-01';

	// Copied once, like MovieForm: the form owns its values after it is created.
	const start = untrack(() => initial);
	let name = $state(start?.name ?? '');
	let birthDate = $state(start?.birthDate ?? '');
	let bio = $state(start?.bio ?? '');
	const today = localToday();

	let saving = $state(false);
	let errors = $state<Record<string, string>>({});
	let formError = $state<{ message: string; conflict: boolean } | null>(null);
	let alert: HTMLElement | undefined = $state();

	/** Mirrors the catalog's rules for instant feedback; the catalog still has the final say. */
	function validate(): Record<string, string> {
		const found: Record<string, string> = {};
		if (!name.trim()) found.name = 'Enter a name.';
		else if (name.trim().length > 200) found.name = 'Use at most 200 characters.';
		// ISO dates compare correctly as strings.
		if (birthDate && birthDate < EARLIEST_BIRTH)
			found.birthDate = 'Enter a date from 1800 onwards.';
		else if (birthDate && birthDate > today)
			found.birthDate = 'A birth date cannot be in the future.';
		if (bio.trim().length > 2000) found.bio = 'Use at most 2000 characters.';
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
		// The fields are locked until the save finishes, as in MovieForm.
		saving = true;
		let failure: SaveFailure | null = null;
		try {
			// Every editable field is sent: updates replace the whole record (DECISIONS D5).
			await onSubmit({ name, birthDate: birthDate || null, bio: bio || null });
		} catch (error) {
			failure = saveFailure(
				error,
				FIELDS,
				'Someone else changed this person while you were editing. Reload to see the latest version; your changes here will be lost.'
			);
		} finally {
			saving = false;
		}
		if (failure) await showFailure(failure);
	}

	/** Locking the fields took focus off the form, so focus goes back to what needs attention. */
	async function showFailure(failure: SaveFailure) {
		if (failure.kind === 'fields') errors = failure.errors;
		else formError = failure;
		await tick();
		if (failure.kind === 'fields') document.getElementById(Object.keys(errors)[0])?.focus();
		else alert?.focus();
	}
</script>

<form class="panel form" onsubmit={submit} novalidate aria-busy={saving}>
	{#if formError}
		<div class="alert" role="alert" tabindex="-1" bind:this={alert}>
			<p>{formError.message}</p>
			{#if formError.conflict && onReload}
				<button class="button" type="button" onclick={onReload}>Reload person</button>
			{/if}
		</div>
	{/if}

	<fieldset disabled={saving}>
		<div class="row">
			<div class="field">
				<label for="name">Name <span aria-hidden="true">*</span></label>
				<input
					id="name"
					bind:value={name}
					required
					maxlength="200"
					autocomplete="off"
					aria-invalid={!!errors.name}
					aria-describedby={errors.name ? 'name-error' : undefined}
				/>
				{#if errors.name}<p class="error" id="name-error">{errors.name}</p>{/if}
			</div>

			<div class="field">
				<label for="birthDate">Birth date</label>
				<input
					id="birthDate"
					type="date"
					bind:value={birthDate}
					min={EARLIEST_BIRTH}
					max={today}
					aria-invalid={!!errors.birthDate}
					aria-describedby={errors.birthDate ? 'birthDate-error' : undefined}
				/>
				{#if errors.birthDate}<p class="error" id="birthDate-error">{errors.birthDate}</p>{/if}
			</div>
		</div>

		<div class="field">
			<label for="bio">Biography</label>
			<textarea
				id="bio"
				rows="5"
				maxlength="2000"
				bind:value={bio}
				aria-invalid={!!errors.bio}
				aria-describedby={errors.bio ? 'bio-error' : undefined}></textarea>
			{#if errors.bio}<p class="error" id="bio-error">{errors.bio}</p>{/if}
		</div>
	</fieldset>

	<div class="actions">
		<a class="button" href={cancelHref}>Cancel</a>
		<button class="button primary" type="submit" disabled={saving}>
			{saving ? 'Saving…' : submitLabel}
		</button>
	</div>
</form>
