<script lang="ts">
	import { dismissToast, toasts } from '#lib/toasts.svelte.ts';
</script>

<div class="toasts" aria-live="polite">
	{#each toasts as toast (toast.id)}
		<div class="toast {toast.kind}" role="status">
			<span>{toast.message}</span>
			<button type="button" aria-label="Dismiss" onclick={() => dismissToast(toast.id)}>×</button>
		</div>
	{/each}
</div>

<style>
	.toasts {
		position: fixed;
		right: 16px;
		bottom: 16px;
		left: 16px;
		display: grid;
		justify-items: end;
		gap: 8px;
		pointer-events: none;
		z-index: 20;
	}

	.toast {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		max-width: 420px;
		padding: 0.75rem 1rem;
		border-radius: 8px;
		background: var(--surface);
		border: 1px solid var(--border);
		border-left: 4px solid var(--success);
		box-shadow: var(--shadow);
		pointer-events: auto;
	}

	.toast.error {
		border-left-color: var(--danger);
	}

	button {
		border: 0;
		background: none;
		color: var(--text-muted);
		font-size: 1.25rem;
		cursor: pointer;
	}
</style>
