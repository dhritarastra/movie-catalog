export interface Toast {
	id: number;
	kind: 'success' | 'error';
	message: string;
}

let nextId = 1;

export const toasts = $state<Toast[]>([]);

export function showToast(kind: Toast['kind'], message: string, durationMs = 4000) {
	const id = nextId++;
	toasts.push({ id, kind, message });
	setTimeout(() => dismissToast(id), durationMs);
}

export function dismissToast(id: number) {
	const index = toasts.findIndex((t) => t.id === id);
	if (index >= 0) toasts.splice(index, 1);
}
