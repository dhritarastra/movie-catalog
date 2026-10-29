<script lang="ts">
	import { resolve } from '$app/paths';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import { formatDate, yearOf } from '#lib/format.ts';
	import { describeError, GraphQlRequestError, request } from '#lib/graphql/client.ts';
	import { describeRole, resolved } from '#lib/credits.ts';
	import type {
		PersonDetailFieldsFragment,
		PersonQuery as PersonQueryResult
	} from '#lib/graphql/generated/graphql.ts';
	import { DeletePersonMutation, PersonQuery } from '#lib/graphql/operations.ts';
	import { trackVisit } from '#lib/navigation.ts';
	import { showToast } from '#lib/toasts.svelte.ts';

	type View =
		| { status: 'loading' }
		| { status: 'not-found' }
		| { status: 'error'; message: string }
		| { status: 'ready'; person: NonNullable<PersonQueryResult['person']> };

	const id = $derived(page.params.id ?? '');
	let view = $state<View>({ status: 'loading' });
	let attempt = $state(0);

	$effect(() => {
		const personId = id;
		void attempt;
		const controller = new AbortController();
		// Back/Forward can reuse this component for another ID while an action is pending.
		confirming = false;
		busy = false;
		confirmError = null;
		view = { status: 'loading' };
		request(PersonQuery, { id: personId }, { signal: controller.signal })
			.then(
				(data) =>
					(view = data.person ? { status: 'ready', person: data.person } : { status: 'not-found' })
			)
			.catch((error) => {
				if (error?.name !== 'AbortError') view = { status: 'error', message: describeError(error) };
			});
		return () => controller.abort();
	});

	let confirming = $state(false);
	let busy = $state(false);
	let confirmError = $state<string | null>(null);

	const visit = trackVisit();

	/** Several credits can be on one movie (cast and director, say), so movies are counted once. */
	function creditedMovies(person: NonNullable<PersonQueryResult['person']>): number {
		return new Set(resolved(person.credits, 'movie').map((credit) => credit.movie.id)).size;
	}

	/** Like deleting a movie: if the user leaves while this runs, they get a toast but stay where they went. */
	async function remove(person: PersonDetailFieldsFragment) {
		const stillHere = visit.mark();
		busy = true;
		confirmError = null;
		try {
			await request(DeletePersonMutation, { id: person.id, version: person.version });
			if (stillHere()) confirming = false;
			showToast('success', `${person.name} was deleted.`);
			if (stillHere()) await goto(resolve('/people'));
		} catch (error) {
			if (!stillHere()) {
				showToast('error', `Deletion of “${person.name}”: ${describeError(error)}`);
				return;
			}
			if (error instanceof GraphQlRequestError && error.code === 'CONFLICT') {
				confirming = false;
				// Nothing was deleted. Show the latest version so the user decides again knowingly.
				attempt++;
				showToast(
					'error',
					'Someone else changed this person, so nothing was deleted. This is the latest version; try again if you still want to.'
				);
			} else {
				// Includes FAILED_PRECONDITION, whose message explains what has to change first.
				confirmError = describeError(error);
			}
		} finally {
			if (stillHere()) busy = false;
		}
	}
</script>

<svelte:head>
	<title>{view.status === 'ready' ? view.person.name : 'Person'} · Movie Catalog</title>
</svelte:head>

<p><a href={resolve('/people')}>← All people</a></p>

{#if view.status === 'loading'}
	<div class="details" aria-busy="true">
		<div class="skeleton line wide"></div>
		<div class="skeleton line"></div>
		<div class="skeleton line"></div>
		<p class="visually-hidden" aria-live="polite">Loading person</p>
	</div>
{:else if view.status === 'not-found'}
	<div class="panel">
		<h1>Person not found</h1>
		<p class="muted">They may have been deleted, or the link is wrong.</p>
	</div>
{:else if view.status === 'error'}
	<div class="alert" role="alert">
		<p>{view.message}</p>
		<button class="button" type="button" onclick={() => attempt++}>Retry</button>
	</div>
{:else}
	{@const person = view.person}
	{@const credits = resolved(person.credits, 'movie')}
	<article class="details">
		<div class="title-row">
			<h1>{person.name}</h1>
			<div class="actions">
				<a class="button" href={resolve('/people/[id]/edit', { id: person.id })}>Edit</a>
				<button
					class="button"
					type="button"
					onclick={() => {
						confirmError = null;
						confirming = true;
					}}>Delete</button
				>
			</div>
		</div>
		{#if person.birthDate}<p class="muted">Born {formatDate(person.birthDate)}</p>{/if}
		{#if person.bio}<p class="bio">{person.bio}</p>{/if}

		<section class="credits-section" aria-labelledby="credits-heading">
			<h2 id="credits-heading">Credits</h2>
			{#if credits.length === 0}
				<p class="muted">No credits yet. Credit them from a movie's page.</p>
			{:else}
				<ul class="credits" aria-label="Credits">
					{#each credits as credit (credit.id)}
						<li>
							<a href={resolve('/movies/[id]', { id: credit.movie.id })}>{credit.movie.title}</a>
							{#if credit.movie.releaseDate}
								<span class="muted">({yearOf(credit.movie.releaseDate)})</span>
							{/if}
							<span class="muted">· {describeRole(credit)}</span>
						</li>
					{/each}
				</ul>
			{/if}
		</section>
	</article>

	<ConfirmDialog
		open={confirming}
		title={`Delete ${person.name}?`}
		message={creditedMovies(person) > 0
			? `${person.name} is credited on ${creditedMovies(person) === 1 ? 'a movie' : `${creditedMovies(person)} movies`}. Remove those credits first.`
			: 'This person will be permanently deleted.'}
		confirmLabel="Delete person"
		{busy}
		error={confirmError}
		onConfirm={() => remove(person)}
		onCancel={() => !busy && (confirming = false)}
	/>
{/if}

<style>
	.details {
		display: grid;
		gap: 0.75rem;
		max-width: 720px;
	}

	.details p {
		margin: 0;
	}

	.title-row {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 1rem;
		flex-wrap: wrap;
	}

	.title-row h1 {
		margin: 0;
		overflow-wrap: anywhere;
	}

	.title-row .actions {
		display: flex;
		gap: 0.5rem;
	}

	.credits-section {
		display: grid;
		gap: 0.5rem;
		margin-top: 1rem;
	}

	.credits-section h2 {
		margin: 0;
	}

	.credits {
		display: grid;
		gap: 0.25rem;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.credits li {
		display: flex;
		gap: 0.4rem;
		flex-wrap: wrap;
		padding: 0.4rem 0;
		border-bottom: 1px solid var(--border);
	}

	.bio {
		max-width: 65ch;
		white-space: pre-line;
	}

	.skeleton.line {
		height: 18px;
		width: 50%;
	}

	.skeleton.line.wide {
		height: 32px;
		width: 70%;
	}

	.alert {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
		flex-wrap: wrap;
	}

	.alert p {
		margin: 0;
	}
</style>
