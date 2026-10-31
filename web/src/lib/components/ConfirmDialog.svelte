<script lang="ts">
	interface Props {
		open: boolean;
		title: string;
		message: string;
		confirmLabel: string;
		busy: boolean;
		error?: string | null;
		onConfirm(): void;
		onCancel(): void;
	}

	let {
		open,
		title,
		message,
		confirmLabel,
		busy,
		error = null,
		onConfirm,
		onCancel
	}: Props = $props();

	let dialog: HTMLDialogElement | undefined = $state();
	const uid = $props.id();

	// Native modal dialog: traps focus, starts on Cancel and closes on Escape.
	$effect(() => {
		if (!dialog) return;
		if (open && !dialog.open) dialog.showModal();
		if (!open && dialog.open) dialog.close();
	});
</script>

<dialog
	bind:this={dialog}
	aria-labelledby="{uid}-title"
	aria-describedby="{uid}-message"
	onclose={onCancel}
	oncancel={(event) => busy && event.preventDefault()}
>
	<h2 id="{uid}-title">{title}</h2>
	<p id="{uid}-message">{message}</p>
	{#if error}<p class="alert" role="alert">{error}</p>{/if}
	<div class="actions">
		<button class="button" type="button" onclick={onCancel} disabled={busy}>Cancel</button>
		<button class="button danger" type="button" onclick={onConfirm} disabled={busy}>
			{busy ? 'Working…' : confirmLabel}
		</button>
	</div>
</dialog>

<style>
	dialog {
		width: min(440px, calc(100vw - 32px));
		padding: 1.25rem;
		border: 1px solid var(--border);
		border-radius: var(--radius);
		background: var(--surface);
		color: var(--text);
		box-shadow: var(--shadow);
	}

	dialog::backdrop {
		background: rgb(0 0 0 / 45%);
	}

	h2 {
		margin: 0 0 0.5rem;
		font-size: 1.125rem;
	}

	p {
		margin: 0 0 1rem;
	}

	.actions {
		display: flex;
		justify-content: flex-end;
		gap: 0.75rem;
	}
</style>
