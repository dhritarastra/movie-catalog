<script lang="ts">
	import { resolve } from '$app/paths';
	import type { ResolvedPathname } from '$app/types';
	import { page } from '$app/state';
	import { yearOf } from '#lib/format.ts';
	import { describeReadError, request } from '#lib/graphql/client.ts';
	import type { PeopleQuery as PeopleResult } from '#lib/graphql/generated/graphql.ts';
	import { PeopleQuery } from '#lib/graphql/operations.ts';

	const PAGE_SIZE = 20;

	const pageHref = (n: number) => `${resolve('/people')}?page=${n}` as ResolvedPathname;

	type View =
		| { status: 'loading' }
		| { status: 'error'; message: string }
		| { status: 'ready'; people: PeopleResult['people'] };

	const pageNumber = $derived(
		Math.max(1, Math.floor(Number(page.url.searchParams.get('page'))) || 1)
	);
	let view = $state<View>({ status: 'loading' });
	let attempt = $state(0);

	$effect(() => {
		const offset = (pageNumber - 1) * PAGE_SIZE;
		void attempt;
		const controller = new AbortController();
		view = { status: 'loading' };
		request(PeopleQuery, { page: { offset, limit: PAGE_SIZE } }, { signal: controller.signal })
			.then((data) => (view = { status: 'ready', people: data.people }))
			.catch((error) => {
				if (error?.name !== 'AbortError')
					view = { status: 'error', message: describeReadError(error) };
			});
		return () => controller.abort();
	});

	const lastPage = $derived(
		view.status === 'ready' ? Math.max(1, Math.ceil(view.people.totalCount / PAGE_SIZE)) : 1
	);
</script>

<svelte:head>
	<title>People · Movie Catalog</title>
</svelte:head>

<div class="heading">
	<div>
		<h1>People</h1>
		{#if view.status === 'ready' && view.people.totalCount > 0}
			<p class="muted">
				{view.people.offset + 1}–{view.people.offset + view.people.items.length} of {view.people
					.totalCount}
			</p>
		{/if}
	</div>
	<a class="button primary" href={resolve('/people/new')}>Add person</a>
</div>

<p class="visually-hidden" aria-live="polite">
	{view.status === 'loading'
		? 'Loading people'
		: view.status === 'ready'
			? `${view.people.items.length} people shown`
			: ''}
</p>

{#if view.status === 'loading'}
	<ul class="list panel" aria-busy="true" aria-label="Loading people">
		{#each { length: 6 }, i (i)}
			<li class="row" aria-hidden="true">
				<div class="skeleton circle"></div>
				<div class="skeleton line"></div>
			</li>
		{/each}
	</ul>
{:else if view.status === 'error'}
	<div class="alert" role="alert">
		<p>{view.message}</p>
		<button class="button" type="button" onclick={() => attempt++}>Retry</button>
	</div>
{:else if view.people.totalCount === 0}
	<div class="panel empty">
		<h2>No people yet</h2>
		<p class="muted">Add cast members and creators, then credit them on movies.</p>
	</div>
{:else if view.people.items.length === 0}
	<div class="panel empty">
		<h2>This page is empty</h2>
		<a class="button" href={pageHref(lastPage)}>Go to the last page</a>
	</div>
{:else}
	<ul class="list panel">
		{#each view.people.items as person (person.id)}
			{@const born = yearOf(person.birthDate)}
			<li>
				<a class="row" href={resolve('/people/[id]', { id: person.id })}>
					<span class="avatar" aria-hidden="true">{person.name.slice(0, 1).toUpperCase()}</span>
					<span class="name">{person.name}</span>
					{#if born}<span class="muted">Born {born}</span>{/if}
				</a>
			</li>
		{/each}
	</ul>

	{#if lastPage > 1}
		<nav class="pager" aria-label="Pagination">
			<a
				class="button"
				href={pageHref(pageNumber - 1)}
				aria-disabled={pageNumber <= 1}
				tabindex={pageNumber <= 1 ? -1 : undefined}
				onclick={(e) => pageNumber <= 1 && e.preventDefault()}>Previous</a
			>
			<span class="muted">Page {pageNumber} of {lastPage}</span>
			<a
				class="button"
				href={pageHref(pageNumber + 1)}
				aria-disabled={pageNumber >= lastPage}
				tabindex={pageNumber >= lastPage ? -1 : undefined}
				onclick={(e) => pageNumber >= lastPage && e.preventDefault()}>Next</a
			>
		</nav>
	{/if}
{/if}

<style>
	.heading {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 1rem;
		margin-bottom: 1rem;
	}

	.heading h1,
	.heading p {
		margin: 0;
	}

	.list {
		display: grid;
		margin: 0;
		padding: 0.25rem 0;
		list-style: none;
	}

	.list li + li {
		border-top: 1px solid var(--border);
	}

	.row {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		min-height: 56px;
		padding: 0.5rem 1.25rem;
		color: var(--text);
		text-decoration: none;
	}

	a.row:hover .name {
		text-decoration: underline;
	}

	.name {
		flex: 1;
		min-width: 0;
		font-weight: 600;
		overflow-wrap: anywhere;
	}

	.avatar {
		display: grid;
		place-items: center;
		flex: none;
		width: 36px;
		height: 36px;
		border-radius: 50%;
		background: var(--surface-muted);
		color: var(--text-muted);
		font-weight: 700;
	}

	.skeleton.circle {
		flex: none;
		width: 36px;
		height: 36px;
		border-radius: 50%;
	}

	.skeleton.line {
		height: 14px;
		width: 40%;
	}

	.empty {
		text-align: center;
		padding-block: 3rem;
	}

	.empty h2 {
		margin-top: 0;
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

	.pager {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 1rem;
		margin-top: 2rem;
	}
</style>
