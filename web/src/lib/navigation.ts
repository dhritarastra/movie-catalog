import { beforeNavigate } from '$app/navigation';

/**
 * Tells a page whether the user is still on it when slow work, such as a save, finishes.
 *
 * A mutation keeps running after the user leaves, and aborting its request would not undo a
 * change the server may already have made. So its completion can still confirm the outcome
 * with a toast, but must not navigate or update the page the user has left.
 *
 * Call during component setup. `mark()` is called when the work starts; the check it returns
 * is false once any navigation has started since, including to a new copy of the same page or
 * to another ID on the same route (where SvelteKit reuses the component).
 */
export function trackVisit(): { mark(): () => boolean } {
	let navigations = 0;
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
