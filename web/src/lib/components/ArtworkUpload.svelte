<script lang="ts">
	import { describeError, GraphQlRequestError } from '#lib/graphql/client.ts';
	import type { MovieDetailFieldsFragment } from '#lib/graphql/generated/graphql.ts';
	import {
		ACCEPTED_TYPES,
		checkFile,
		uploadArtwork,
		type UploadHandle
	} from '#lib/graphql/upload.ts';

	interface Props {
		movieId: string;
		/** The version the user is looking at; the upload is refused if the movie has changed since. */
		version: number;
		hasArtwork: boolean;
		onUploaded(movie: MovieDetailFieldsFragment): void;
		onReload(): void;
	}

	let { movieId, version, hasArtwork, onUploaded, onReload }: Props = $props();

	type Phase =
		| { name: 'idle' }
		| { name: 'uploading'; progress: number }
		| { name: 'processing' }
		| { name: 'error'; message: string; reload: boolean };

	let input: HTMLInputElement | undefined = $state();
	let file = $state<File | null>(null);
	let fileError = $state<string | null>(null);
	let phase = $state<Phase>({ name: 'idle' });
	let handle: UploadHandle | null = null;

	const previewUrl = $derived(file ? URL.createObjectURL(file) : null);
	$effect(() => {
		const url = previewUrl;
		return () => {
			if (url) URL.revokeObjectURL(url);
		};
	});

	const busy = $derived(phase.name === 'uploading' || phase.name === 'processing');
	const percent = $derived(phase.name === 'uploading' ? Math.round(phase.progress * 100) : 0);
	// Announced to screen readers in 25% steps, so they are not flooded with every progress event.
	const announcement = $derived(
		phase.name === 'uploading'
			? `Uploading, ${Math.floor(phase.progress * 4) * 25} percent`
			: phase.name === 'processing'
				? 'Upload complete. Processing artwork.'
				: phase.name === 'error'
					? phase.message
					: ''
	);

	function choose() {
		const chosen = input?.files?.[0] ?? null;
		phase = { name: 'idle' };
		fileError = chosen ? checkFile(chosen) : null;
		file = chosen && !fileError ? chosen : null;
	}

	async function start() {
		if (!file || busy) return;
		phase = { name: 'uploading', progress: 0 };
		handle = uploadArtwork(movieId, version, file, {
			onProgress: (progress) => {
				if (phase.name === 'uploading') phase = { name: 'uploading', progress };
			},
			onProcessing: () => (phase = { name: 'processing' })
		});
		try {
			const movie = await handle.result;
			file = null;
			if (input) input.value = '';
			phase = { name: 'idle' };
			onUploaded(movie);
		} catch (error) {
			if (error instanceof DOMException && error.name === 'AbortError') {
				phase = { name: 'idle' };
			} else {
				const code = error instanceof GraphQlRequestError ? error.code : null;
				phase = {
					name: 'error',
					message: describeError(error),
					reload: code === 'CONFLICT' || code === 'SERVICE_UNAVAILABLE'
				};
			}
		} finally {
			handle = null;
		}
	}
</script>

<section class="panel upload" aria-labelledby="artwork-heading">
	<h2 id="artwork-heading">{hasArtwork ? 'Replace artwork' : 'Add artwork'}</h2>
	<p class="muted">JPEG or PNG, up to 5 MB.</p>

	<div class="picker">
		<label class="button" for="artwork-file" aria-disabled={busy}>Choose image</label>
		<input
			id="artwork-file"
			class="visually-hidden"
			type="file"
			accept={ACCEPTED_TYPES.join(',')}
			bind:this={input}
			onchange={choose}
			disabled={busy}
			aria-describedby={fileError ? 'artwork-file-error' : undefined}
		/>
		<span class="muted filename"
			>{file
				? `${file.name} (${(file.size / (1024 * 1024)).toFixed(1)} MB)`
				: 'No file chosen'}</span
		>
	</div>
	{#if fileError}<p class="error" id="artwork-file-error" role="alert">{fileError}</p>{/if}

	{#if previewUrl}
		<img class="preview" src={previewUrl} alt="Preview of {file?.name}" />
	{/if}

	{#if phase.name === 'uploading'}
		<div class="progress">
			<progress max="100" value={percent} aria-label="Upload progress"></progress>
			<span>{percent}%</span>
		</div>
	{:else if phase.name === 'processing'}
		<div class="progress">
			<progress aria-label="Processing artwork"></progress>
			<span>Processing…</span>
		</div>
		<p class="muted small">
			The image has been sent and is being checked and stored. This can't be cancelled now.
		</p>
	{:else if phase.name === 'error'}
		<div class="alert" role="alert">
			<p>{phase.message}</p>
			{#if phase.reload}<button class="button" type="button" onclick={onReload}>Reload movie</button
				>{/if}
		</div>
	{/if}

	<div class="actions">
		{#if phase.name === 'uploading'}
			<button class="button" type="button" onclick={() => handle?.cancel()}>Cancel upload</button>
		{/if}
		<button class="button primary" type="button" onclick={start} disabled={!file || busy}>
			{busy ? 'Uploading…' : 'Upload'}
		</button>
	</div>

	<p class="visually-hidden" aria-live="polite">{announcement}</p>
</section>

<style>
	.upload {
		display: grid;
		gap: 0.75rem;
	}

	h2 {
		margin: 0;
		font-size: 1.125rem;
	}

	p {
		margin: 0;
	}

	.picker {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		flex-wrap: wrap;
	}

	.picker:focus-within label {
		outline: 3px solid var(--accent);
		outline-offset: 2px;
	}

	.filename {
		overflow-wrap: anywhere;
	}

	.preview {
		max-width: 160px;
		max-height: 240px;
		border-radius: 8px;
		border: 1px solid var(--border);
		object-fit: contain;
	}

	.progress {
		display: flex;
		align-items: center;
		gap: 0.75rem;
	}

	progress {
		flex: 1;
		height: 10px;
		accent-color: var(--accent);
	}

	.error {
		color: var(--danger);
		font-size: 0.875rem;
	}

	.small {
		font-size: 0.875rem;
	}

	.alert {
		display: grid;
		gap: 0.5rem;
		justify-items: start;
	}

	.actions {
		display: flex;
		justify-content: flex-end;
		gap: 0.75rem;
	}
</style>
