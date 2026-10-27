import { expect, test, type Page } from '@playwright/test';
import { png } from './images';

/**
 * The browser smoke test (docs/PLAN.md §9): one flow through the whole stack, extended as
 * features arrive. Everything else is covered by faster tests at lower layers.
 */
test('create a movie, upload artwork and see it everywhere, with failures explained', async ({
	page
}) => {
	const title = `Smoke test ${Date.now()}`;

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
});

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
