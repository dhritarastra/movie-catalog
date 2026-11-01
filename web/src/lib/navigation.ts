import { onDestroy } from 'svelte';
import { beforeNavigate } from '$app/navigation';

/**
 * Tells a page whether the user is still on it when a save finishes. The save itself still
 * completes, but must not navigate or update a page the user has left.
 */
export function trackVisit(): { mark(): () => boolean } {
	let navigations = 0;
	onDestroy(() => {
		navigations++;
	});
	beforeNavigate(() => {
		navigations++;
	});
	return {
		mark() {
			const at = navigations;
			return () => navigations === at;
		}
	};
}
