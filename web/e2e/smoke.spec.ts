import { expect, test, type Page, type Request } from '@playwright/test';
import { png } from './images';

/**
 * The browser smoke test: one flow through the whole stack, extended as
 * features arrive. Everything else is covered by faster tests at lower layers.
 */
test('create, edit and delete a movie with artwork, and a person, with failures explained', async ({
	page
}) => {
	const stamp = Date.now();
	const title = `Smoke test ${stamp}`;
	const editedTitle = `${title} (edited)`;
	// Early in the alphabet, so the person is on the first page of the list even with other data.
	const person = `Ada Smoke ${stamp}`;
	const editedPerson = `${person} Jr`;

	await test.step('the movie list loads', async () => {
		await page.goto('/movies');
		await expect(page.getByRole('heading', { name: 'Movies', level: 1 })).toBeVisible();
	});

	await test.step('the form explains a missing title before sending anything', async () => {
		await page.getByRole('link', { name: 'Add movie' }).first().click();
		await page.getByRole('button', { name: 'Add movie' }).click();
		await expect(page.getByText('Enter a title.')).toBeVisible();
	});

	await test.step('creating the movie opens its page with a confirmation', async () => {
		await page.getByLabel('Title').fill(title);
		await page.getByLabel('Release date').fill('2017-10-06');
		await page.getByLabel('Runtime (minutes)').fill('164');
		await page.getByLabel('Genres').fill('Sci-Fi, Neo-noir');
		await page.getByRole('button', { name: 'Add movie' }).click();

		await expect(page.getByRole('heading', { name: title, level: 1 })).toBeVisible();
		await expect(page.getByText(`“${title}” was added`)).toBeVisible();
		await expect(page.getByText('No artwork yet')).toBeVisible();
	});

	await test.step('uploading artwork shows it on the page', async () => {
		await chooseFile(page, 'poster.png', 'image/png', png(600, 900));
		await expect(page.getByAltText('Preview of poster.png')).toBeVisible();
		await page.getByRole('button', { name: 'Upload', exact: true }).click();

		await expect(page.getByText('Artwork updated.')).toBeVisible();
		await expectImageLoaded(page, `Artwork for ${title}`);
		await expect(page.getByRole('heading', { name: 'Replace artwork' })).toBeVisible();
	});

	await test.step('the movie survives a reload of its own URL', async () => {
		await page.reload();
		await expect(page.getByRole('heading', { name: title, level: 1 })).toBeVisible();
		await expectImageLoaded(page, `Artwork for ${title}`);
	});

	await test.step('a file that only claims to be a PNG is refused with a clear message', async () => {
		await chooseFile(
			page,
			'not-really.png',
			'image/png',
			Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"/>')
		);
		await page.getByRole('button', { name: 'Upload', exact: true }).click();

		await expect(page.getByRole('alert')).toContainText('Only JPEG and PNG images are supported');
	});

	await test.step('the browser rejects other file types before uploading', async () => {
		await chooseFile(page, 'animation.gif', 'image/gif', Buffer.from('GIF89a'));

		await expect(page.getByText('Choose a JPEG or PNG image.')).toBeVisible();
		await expect(page.getByRole('button', { name: 'Upload', exact: true })).toBeDisabled();
	});

	await test.step('the list shows the movie with its thumbnail', async () => {
		await page.getByRole('link', { name: '← All movies' }).click();
		const card = page.getByRole('link', { name: new RegExp(title) });
		await expect(card).toBeVisible();
		await expect(card.locator('img')).toHaveJSProperty('complete', true);
		expect(await card.locator('img').evaluate((img: HTMLImageElement) => img.naturalWidth)).toBe(
			400
		);
	});

	await test.step('editing replaces the details and keeps the artwork; the form is locked while saving', async () => {
		await page.getByRole('link', { name: new RegExp(title) }).click();
		await page.getByRole('link', { name: 'Edit', exact: true }).click();
		await expect(page.getByLabel('Title')).toHaveValue(title);
		await page.getByLabel('Title').fill(editedTitle);
		await page.getByLabel('Runtime (minutes)').fill('165');

		// While the answer is on its way, nothing can be typed that the save would then discard.
		const save = await holdResponse(page, 'UpdateMovie');
		await page.getByRole('button', { name: 'Save changes' }).click();
		await save.answered;
		await expect(page.getByLabel('Title')).toBeDisabled();
		await expect(page.getByLabel('Runtime (minutes)')).toBeDisabled();
		await expect(page.getByRole('button', { name: 'Saving…' })).toBeDisabled();
		// A fragment change leaves this form on screen and must not invalidate the save.
		await page.evaluate(() => {
			location.hash = 'saving';
		});
		await expect(page).toHaveURL(/#saving$/);
		save.release();

		await expect(page.getByText('Changes saved.')).toBeVisible();
		await expect(page.getByRole('heading', { name: editedTitle, level: 1 })).toBeVisible();
		await expect(page.getByText('2017 · 2h 45m')).toBeVisible();
		await expectImageLoaded(page, `Artwork for ${editedTitle}`);
	});

	await test.step('an edit that would overwrite a newer change is refused, and Reload shows it', async () => {
		await page.getByRole('link', { name: 'Edit', exact: true }).click();
		await expect(page.getByLabel('Title')).toHaveValue(editedTitle);
		// Someone else saves a change while this form is open.
		await renameElsewhere(page, page.url().split('/').at(-2)!, `${title} (changed elsewhere)`);

		await page.getByLabel('Title').fill(`${title} (my late edit)`);
		await page.getByRole('button', { name: 'Save changes' }).click();
		await expect(page.getByRole('alert')).toContainText('Someone else changed this movie');

		await page.getByRole('button', { name: 'Reload movie' }).click();
		await expect(page.getByLabel('Title')).toHaveValue(`${title} (changed elsewhere)`);
		await page.getByRole('link', { name: 'Cancel' }).click();
	});

	await test.step('leaving while a save is in flight: it is confirmed, but does not pull you back', async () => {
		// Wait out the earlier "Changes saved." toast, so the one checked below is this save's.
		await expect(page.getByText('Changes saved.')).toHaveCount(0, { timeout: 10_000 });
		await page.getByRole('link', { name: 'Edit', exact: true }).click();
		await expect(page.getByLabel('Title')).toHaveValue(`${title} (changed elsewhere)`);
		await page.getByLabel('Genres').fill('Sci-Fi, Neo-noir, Thriller');

		const save = await holdResponse(page, 'UpdateMovie');
		await page.getByRole('button', { name: 'Save changes' }).click();
		await save.answered;
		await page.getByRole('link', { name: 'Cancel' }).click();
		await page.getByRole('link', { name: '← All movies' }).click();
		await expect(page.getByRole('heading', { name: 'Movies', level: 1 })).toBeVisible();
		save.release();

		const toast = page.getByText('Changes saved.');
		await expect(toast).toBeVisible();
		// A redirect would follow the toast within milliseconds; the toast stays for seconds.
		await expect(toast).toBeHidden({ timeout: 10_000 });
		await expect(page).toHaveURL(/\/movies$/);

		// The save itself went through.
		await page.getByRole('link', { name: new RegExp(title) }).click();
		await expect(page.getByRole('list', { name: 'Genres' })).toContainText('Thriller');
	});

	await test.step('late movie save failures notify without touching a new draft', async () => {
		await checkLateSaveFailures(page, 'movie', `${title} (changed elsewhere)`);
		await checkPendingDeleteNavigation(page, 'movie', `${title} (changed elsewhere)`);
	});

	await test.step('removing the artwork asks first, then shows the placeholder', async () => {
		await page.getByRole('button', { name: 'Remove artwork' }).click();
		const dialog = page.getByRole('dialog', { name: 'Remove the artwork?' });
		await dialog.getByRole('button', { name: 'Remove artwork' }).click();

		await expect(page.getByText('Artwork removed.')).toBeVisible();
		await expect(page.getByText('No artwork yet')).toBeVisible();
	});

	await test.step('deleting asks first; cancelling keeps the movie, confirming removes it', async () => {
		const name = `${title} (changed elsewhere)`;
		await page.getByRole('button', { name: 'Delete', exact: true }).click();
		await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
		await expect(page.getByRole('heading', { name, level: 1 })).toBeVisible();

		await page.getByRole('button', { name: 'Delete', exact: true }).click();
		await page
			.getByRole('dialog', { name: `Delete “${name}”?` })
			.getByRole('button', { name: 'Delete movie' })
			.click();

		await expect(page.getByText(`“${name}” was deleted.`)).toBeVisible();
		await expect(page).toHaveURL(/\/movies$/);
		await expect(page.getByRole('link', { name: new RegExp(title) })).toHaveCount(0);
	});

	await test.step('the person form explains a missing name and a future birth date', async () => {
		await page
			.getByRole('navigation', { name: 'Main' })
			.getByRole('link', { name: 'People' })
			.click();
		await expect(page.getByRole('heading', { name: 'People', level: 1 })).toBeVisible();
		await page.getByRole('link', { name: 'Add person' }).click();

		await page.getByLabel('Birth date').fill('2999-01-01');
		await page.getByRole('button', { name: 'Add person' }).click();
		await expect(page.getByText('Enter a name.')).toBeVisible();
		await expect(page.getByText('A birth date cannot be in the future.')).toBeVisible();
	});

	await test.step('adding the person opens their page, which survives a reload', async () => {
		await page.getByLabel('Name').fill(person);
		await page.getByLabel('Birth date').fill('1967-10-03');
		await page.getByLabel('Biography').fill('Added by the smoke test.');
		await page.getByRole('button', { name: 'Add person' }).click();

		await expect(page.getByRole('heading', { name: person, level: 1 })).toBeVisible();
		await expect(page.getByText(`${person} was added.`)).toBeVisible();
		await expect(page.getByText('Born 3 October 1967')).toBeVisible();

		await page.reload();
		await expect(page.getByRole('heading', { name: person, level: 1 })).toBeVisible();
	});

	await test.step('the person is listed, and editing replaces every field', async () => {
		await page.getByRole('link', { name: '← All people' }).click();
		const row = page.getByRole('link', { name: new RegExp(person) });
		await expect(row).toContainText('Born 1967');
		await row.click();
		await page.getByRole('link', { name: 'Edit', exact: true }).click();
		await expect(page.getByLabel('Name')).toHaveValue(person);
		await page.getByLabel('Name').fill(editedPerson);
		await page.getByLabel('Biography').fill('');

		const save = await holdResponse(page, 'UpdatePerson');
		await page.getByRole('button', { name: 'Save changes' }).click();
		await save.answered;
		await expect(page.getByLabel('Name')).toBeDisabled();
		await expect(page.getByLabel('Biography')).toBeDisabled();
		save.release();

		await expect(page.getByText('Changes saved.')).toBeVisible();
		await expect(page.getByRole('heading', { name: editedPerson, level: 1 })).toBeVisible();
		await expect(page.getByText('Added by the smoke test.')).toHaveCount(0);
		await expect(page.getByText('Born 3 October 1967')).toBeVisible();
	});

	await test.step('late person save failures notify without touching a new draft', async () => {
		await checkLateSaveFailures(page, 'person', editedPerson);
		await checkPendingDeleteNavigation(page, 'person', editedPerson);
	});

	await test.step('deleting the person asks first, then removes them from the list', async () => {
		await page.getByRole('button', { name: 'Delete', exact: true }).click();
		await page
			.getByRole('dialog', { name: `Delete ${editedPerson}?` })
			.getByRole('button', { name: 'Delete person' })
			.click();

		await expect(page.getByText(`${editedPerson} was deleted.`)).toBeVisible();
		await expect(page).toHaveURL(/\/people$/);
		await expect(page.getByRole('link', { name: new RegExp(person) })).toHaveCount(0);
	});
});

/** Changes the movie's title through the API, as another browser tab or user would. */
async function renameElsewhere(page: Page, id: string, newTitle: string) {
	const graphql = async (query: string, variables: Record<string, unknown>) => {
		const response = await page.request.post('/graphql', { data: { query, variables } });
		const body = await response.json();
		expect(body.errors).toBeUndefined();
		return body.data;
	};
	const { movie } = await graphql(
		'query($id: ID!) { movie(id: $id) { version releaseDate runtimeMinutes genres } }',
		{ id }
	);
	await graphql(
		'mutation($id: ID!, $version: Int!, $input: MovieInput!) { updateMovie(id: $id, version: $version, input: $input) { version } }',
		{
			id,
			version: movie.version,
			input: {
				title: newTitle,
				releaseDate: movie.releaseDate,
				runtimeMinutes: movie.runtimeMinutes,
				genres: movie.genres
			}
		}
	);
}

/**
 * Delays the answer to the next `operation` request, as a slow connection would. The request
 * reaches the server at once unless an error response is supplied. The page receives the
 * response only when `release()` is called. `answered` resolves once that response is ready.
 */
async function holdResponse(
	page: Page,
	operation: string,
	error?: { code: string; fieldErrors?: { field: string; message: string }[] }
) {
	let release!: () => void;
	const released = new Promise<void>((resolve) => (release = resolve));
	let markAnswered!: () => void;
	const answered = new Promise<void>((resolve) => (markAnswered = resolve));
	let matched = false;

	await page.route('**/graphql', async (route) => {
		if (matched || operationName(route.request()) !== operation) return route.fallback();
		matched = true;
		// Error cases inject a delayed server response without changing the fixture on the server.
		const response = error ? undefined : await route.fetch();
		markAnswered();
		await released;
		if (error) {
			await route.fulfill({
				json: { data: null, errors: [{ message: 'Review validation failure', extensions: error }] }
			});
		} else {
			await route.fulfill({ response });
		}
	});
	return { answered, release };
}

/** Browser Back/Forward can reuse a detail component even while its modal blocks links. */
async function checkPendingDeleteNavigation(page: Page, entity: 'movie' | 'person', name: string) {
	const path = entity === 'movie' ? 'movies' : 'people';
	const missing = `/${path}/00000000-0000-0000-0000-000000000000`;
	// Seed a same-route history entry through SvelteKit's normal link interception.
	await page.evaluate((href) => {
		const link = document.createElement('a');
		link.href = href;
		document.body.append(link);
		link.click();
		link.remove();
	}, missing);
	await expect(page).toHaveURL(new RegExp(missing + '$'));
	await expect(page.getByRole('heading', { name: /not found/i })).toBeVisible();
	await page.goBack();
	await expect(page.getByRole('heading', { name, exact: true, level: 1 })).toBeVisible();
	const pending = await holdResponse(page, entity === 'movie' ? 'DeleteMovie' : 'DeletePerson', {
		code: 'SERVICE_UNAVAILABLE'
	});
	await page.getByRole('button', { name: 'Delete', exact: true }).click();
	await page
		.getByRole('dialog')
		.getByRole('button', { name: `Delete ${entity}`, exact: true })
		.click();
	await pending.answered;
	await page.goForward();
	await expect(page).toHaveURL(new RegExp(missing + '$'));
	await page.goBack();
	await expect(page.getByRole('heading', { name, exact: true, level: 1 })).toBeVisible();
	await expect(page.getByRole('dialog')).toHaveCount(0);
	pending.release();
	const toast = page
		.getByRole('status')
		.filter({ hasText: name })
		.filter({ hasText: 'check whether your change was saved' });
	await expect(toast).toBeVisible();
	await toast.getByRole('button', { name: 'Dismiss' }).click();
	await page.getByRole('button', { name: 'Delete', exact: true }).click();
	await expect(
		page.getByRole('dialog').getByRole('button', { name: `Delete ${entity}`, exact: true })
	).toBeEnabled();
	await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click();
}

/** Both forms must ignore departed-page failures, while still explaining the save outcome. */
async function checkLateSaveFailures(page: Page, entity: 'movie' | 'person', name: string) {
	const movie = entity === 'movie';
	for (const code of ['BAD_REQUEST', 'SERVICE_UNAVAILABLE']) {
		await page.getByRole('link', { name: 'Edit', exact: true }).click();
		await expect(page.getByLabel(movie ? 'Title' : 'Name')).toHaveValue(name);
		const save = await holdResponse(page, movie ? 'UpdateMovie' : 'UpdatePerson', {
			code,
			fieldErrors:
				code === 'BAD_REQUEST'
					? [{ field: movie ? 'title' : 'name', message: 'Review validation failure' }]
					: undefined
		});
		await page.getByRole('button', { name: 'Save changes' }).click();
		await save.answered;
		await page.getByRole('link', { name: 'Cancel', exact: true }).click();
		await page.getByRole('link', { name: movie ? '← All movies' : '← All people' }).click();
		await page.getByRole('link', { name: movie ? 'Add movie' : 'Add person', exact: true }).click();
		const draft = page.getByLabel(movie ? 'Synopsis' : 'Biography');
		await draft.fill('An unrelated draft');
		await expect(draft).toBeFocused();
		save.release();

		const toast = page.getByRole('status').filter({ hasText: `Save for “${name}”:` });
		await expect(toast).toContainText(
			code === 'BAD_REQUEST' ? 'Review validation failure' : 'check whether your change was saved'
		);
		await expect(draft).toBeFocused();
		await expect(draft).toHaveValue('An unrelated draft');
		await expect(page.getByRole('alert')).toHaveCount(0);
		await expect(page).toHaveURL(movie ? /\/movies\/new$/ : /\/people\/new$/);
		await toast.getByRole('button', { name: 'Dismiss' }).click();
		await page.getByRole('link', { name: 'Cancel', exact: true }).click();
		await page
			.getByRole('link', { name: new RegExp(name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')) })
			.click();
		await expect(page.getByRole('heading', { name, exact: true, level: 1 })).toBeVisible();
	}
}

/** "UpdateMovie" for a JSON request whose query starts `mutation UpdateMovie(`; null otherwise. */
function operationName(request: Request): string | null {
	try {
		const query: unknown = request.postDataJSON()?.query;
		return typeof query === 'string' ? (/^\s*\w+\s+(\w+)/.exec(query)?.[1] ?? null) : null;
	} catch {
		return null; // multipart uploads are not JSON
	}
}

async function chooseFile(page: Page, name: string, mimeType: string, buffer: Buffer) {
	await page.locator('#artwork-file').setInputFiles({ name, mimeType, buffer });
}

async function expectImageLoaded(page: Page, alt: string) {
	const image = page.getByAltText(alt);
	await expect(image).toBeVisible();
	await expect
		.poll(() => image.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth))
		.toBeGreaterThan(0);
}
