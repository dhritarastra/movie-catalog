import { expect, test, type Page } from '@playwright/test';
import { png } from './images';

/**
 * The browser smoke test (docs/PLAN.md §9): one flow through the whole stack, extended as
 * features arrive. Everything else is covered by faster tests at lower layers.
 */
test('create, upload artwork, edit and delete a movie, with failures explained', async ({
	page
}) => {
	const title = `Smoke test ${Date.now()}`;
	const editedTitle = `${title} (edited)`;

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

	await test.step('editing replaces the details and keeps the artwork', async () => {
		await page.getByRole('link', { name: new RegExp(title) }).click();
		await page.getByRole('link', { name: 'Edit', exact: true }).click();
		await expect(page.getByLabel('Title')).toHaveValue(title);
		await page.getByLabel('Title').fill(editedTitle);
		await page.getByLabel('Runtime (minutes)').fill('165');
		await page.getByRole('button', { name: 'Save changes' }).click();

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
