import { expect, test, type Page, type Request } from '@playwright/test';
import { png } from './images';

/**
 * The browser smoke test: one flow through the whole stack, extended as
 * features arrive. Everything else is covered by faster tests at lower layers.
 */
test('create, edit and delete a movie with artwork, a person and their credits, search for them, with failures explained', async ({
	page
}) => {
	const stamp = Date.now();
	const title = `Smoke test ${stamp}`;
	const editedTitle = `${title} (edited)`;
	// Early in the alphabet, so the person is on the first page of the list even with other data.
	const person = `Ada Smoke ${stamp}`;
	const editedPerson = `${person} Jr`;
	const creditedTitle = `Smoke credits ${stamp}`;

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

	await test.step('the person is credited on a movie as cast and as a creator', async () => {
		await page.goto('/movies/new');
		await page.getByLabel('Title').fill(creditedTitle);
		await page.getByRole('button', { name: 'Add movie' }).click();
		await expect(page.getByRole('heading', { name: creditedTitle, level: 1 })).toBeVisible();
		await expect(page.getByText('No cast yet.')).toBeVisible();
		await expect(page.getByText('No creators yet.')).toBeVisible();

		// 100 people who sort before the smoke person fill the first page of any list of people.
		const fillers = await createPeople(page, 100, `Aaa Filler ${stamp}`);
		try {
			await findBeyondTheFirstPage(page, editedPerson);
		} finally {
			// Only needed to fill the first page; removed even if a check above fails.
			await deletePeople(page, fillers);
		}
		await page.getByLabel('Role').selectOption('CAST');
		await page.getByLabel('Character').fill('  Louise Banks ');
		await page.getByLabel('Billing order').fill('1');
		await page.getByRole('button', { name: 'Add credit' }).click();
		await expect(page.getByText(`${editedPerson} was credited (Cast).`)).toBeVisible();
		const cast = page.getByRole('list', { name: 'Cast' });
		await expect(cast).toContainText(editedPerson);
		await expect(cast).toContainText('as Louise Banks');

		await page.getByRole('button', { name: 'Add credit' }).click();
		await choosePerson(page, editedPerson);
		await page.getByLabel('Role').selectOption('DIRECTOR');
		// Only cast credits have a character, so the field goes away.
		await expect(page.getByLabel('Character')).toHaveCount(0);
		// While a form is open, the list's own buttons wait.
		await expect(
			page.getByRole('button', { name: `Edit ${editedPerson}'s credit (Cast)` })
		).toBeDisabled();
		let adds = 0;
		page.on('request', (request) => {
			if (operationName(request) === 'AddCredit') adds++;
		});
		const save = await holdResponse(page, 'AddCredit');
		await page.getByRole('button', { name: 'Add credit' }).click();
		await save.answered;
		const saving = page.getByRole('button', { name: 'Saving…' });
		await expect(saving).toBeDisabled();
		await expect(page.getByLabel('Role')).toBeDisabled();
		// A second click while saving sends nothing.
		await saving.click({ force: true });
		save.release();
		const creators = page.getByRole('list', { name: 'Creators' });
		await expect(creators).toContainText(editedPerson);
		await expect(creators).toContainText('Director');
		expect(adds).toBe(1);
		// Closing the form returns focus to where it was opened from.
		await expect(page.getByRole('button', { name: 'Add credit' })).toBeFocused();
	});

	await test.step('the same role twice is refused with an explanation', async () => {
		await page.getByRole('button', { name: 'Add credit' }).click();
		await choosePerson(page, editedPerson);
		await page.getByLabel('Role').selectOption('DIRECTOR');
		await page.getByRole('button', { name: 'Add credit' }).click();
		await expect(page.getByRole('alert')).toContainText(
			'This person already has this role on this movie.'
		);
		await page.getByRole('button', { name: 'Cancel', exact: true }).click();
		await expect(page.getByRole('list', { name: 'Creators' }).getByRole('listitem')).toHaveCount(1);
	});

	await test.step('a failed search can be retried, and a slower older search never replaces a newer one', async () => {
		await page.getByRole('button', { name: 'Add credit' }).click();
		const search = page.getByRole('searchbox', { name: 'Person', exact: true });

		// The first search is held, as on a slow connection; a newer one is answered meanwhile.
		let releaseStale!: () => void;
		const stale = new Promise<void>((resolve) => (releaseStale = resolve));
		let markAnswered!: () => void;
		const staleAnswered = new Promise<void>((resolve) => (markAnswered = resolve));
		await page.route('**/graphql', async (route) => {
			if (operationName(route.request()) !== 'PersonPicker') return route.fallback();
			if (route.request().postDataJSON()?.variables?.nameContains !== 'Nobody stale')
				return route.fallback();
			await stale;
			try {
				await route.fulfill({
					json: {
						data: {
							people: { totalCount: 1, items: [{ id: crypto.randomUUID(), name: 'Stale Answer' }] }
						}
					}
				});
			} catch {
				// The page has abandoned this request, so it may no longer accept an answer.
			} finally {
				markAnswered();
			}
		});
		const held = page.waitForRequest(
			(request) =>
				operationName(request) === 'PersonPicker' &&
				request.postDataJSON()?.variables?.nameContains === 'Nobody stale'
		);
		await search.fill('Nobody stale');
		const heldRequest = await held;
		// Wait for the newer search's own answer: the person may already be listed from before.
		const newerText = editedPerson.slice(0, -3);
		const newer = page.waitForResponse(
			(response) =>
				operationName(response.request()) === 'PersonPicker' &&
				response.request().postDataJSON()?.variables?.nameContains === newerText
		);
		await search.fill(newerText);
		await newer;
		await expect(page.getByRole('radio', { name: editedPerson })).toBeVisible();
		releaseStale();
		await staleAnswered;
		// Once the browser is done with the old request (answered, or abandoned by the page) and
		// has drawn two frames, an answer it accepted would be on screen.
		await heldRequest.response();
		await page.evaluate(
			() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
		);
		await expect(page.getByRole('radio', { name: 'Stale Answer' })).toHaveCount(0);
		await expect(page.getByRole('radio', { name: editedPerson })).toBeVisible();
		await page.unrouteAll({ behavior: 'wait' });

		// A failed search explains itself and can be retried; nobody matching is said plainly.
		let failed = false;
		await page.route('**/graphql', async (route) => {
			if (failed || operationName(route.request()) !== 'PersonPicker') return route.fallback();
			failed = true;
			await route.fulfill({
				json: {
					data: null,
					errors: [{ message: 'Search is down', extensions: { code: 'SERVICE_UNAVAILABLE' } }]
				}
			});
		});
		await search.fill('Nobody matches this');
		await expect(page.getByRole('alert')).toBeVisible();
		await page.unrouteAll({ behavior: 'wait' });
		await page.getByRole('button', { name: 'Retry' }).click();
		await expect(page.locator('#personId-status')).toContainText(
			"No one's name contains “Nobody matches this”."
		);

		await page.getByRole('button', { name: 'Cancel', exact: true }).click();
	});

	await test.step('editing a credit replaces its character and billing order', async () => {
		await page.getByRole('button', { name: `Edit ${editedPerson}'s credit (Cast)` }).click();
		// The person cannot change, so focus starts on the role.
		await expect(page.getByLabel('Role')).toBeFocused();
		await expect(page.getByRole('textbox', { name: 'Person', exact: true })).toHaveValue(
			editedPerson
		);
		await expect(page.getByLabel('Character')).toHaveValue('Louise Banks');
		await page.getByLabel('Character').fill('Dr. Louise Banks');
		// An empty or too large billing order is explained before anything is sent.
		for (const order of ['', '1001']) {
			await page.getByLabel('Billing order').fill(order);
			await page.getByRole('button', { name: 'Save credit' }).click();
			await expect(page.getByText('Enter a whole number from 0 to 1000.')).toBeVisible();
		}
		await page.getByLabel('Billing order').fill('0');
		await page.getByRole('button', { name: 'Save credit' }).click();

		await expect(page.getByText(`${editedPerson}'s credit was updated.`)).toBeVisible();
		await expect(page.getByRole('list', { name: 'Cast' })).toContainText('as Dr. Louise Banks');
		await expect(page.getByRole('button', { name: 'Add credit' })).toBeFocused();
	});

	await test.step('saving a credit that someone else removed explains it and shows the latest list', async () => {
		await page.getByRole('button', { name: `Edit ${editedPerson}'s credit (Director)` }).click();
		await removeCreditElsewhere(page, 'creators');
		await page.getByRole('button', { name: 'Save credit' }).click();

		await expect(
			page.getByText(`${editedPerson}'s credit had already been removed.`)
		).toBeVisible();
		await expect(page.getByText('No creators yet.')).toBeVisible();
		await expect(page.getByRole('button', { name: 'Save credit' })).toHaveCount(0);

		// Put the director credit back for the steps that follow.
		await page.getByRole('button', { name: 'Add credit' }).click();
		await choosePerson(page, editedPerson);
		await page.getByLabel('Role').selectOption('DIRECTOR');
		await page.getByRole('button', { name: 'Add credit' }).click();
		await expect(page.getByRole('list', { name: 'Creators' })).toContainText('Director');
	});

	await test.step('the header search opens results for movies and people, labelled, with roles', async () => {
		const header = page.getByRole('searchbox', { name: 'Search movies and people' });
		await header.fill(` ${stamp} `);
		await header.press('Enter');

		await expect(page).toHaveURL(new RegExp(`/search\\?q=${stamp}$`));
		await expect(page.getByRole('searchbox', { name: 'Search movies and people' })).toHaveValue(
			String(stamp)
		);
		const rows = page.getByRole('list', { name: 'Results' }).getByRole('listitem');
		const movieRow = rows.filter({ has: page.getByRole('link', { name: creditedTitle }) });
		const personRow = rows.filter({ has: page.getByRole('link', { name: editedPerson }) });
		await expect(movieRow).toContainText('Movie');
		await expect(personRow).toContainText('Person');
		await expect(personRow).toContainText('Cast · Director, 1 movie');
	});

	await test.step('a search result opens its page, and Back returns to the same results', async () => {
		await page
			.getByRole('list', { name: 'Results' })
			.getByRole('link', { name: editedPerson })
			.click();
		await expect(page.getByRole('heading', { name: editedPerson, level: 1 })).toBeVisible();

		await page.goBack();
		await expect(page).toHaveURL(new RegExp(`/search\\?q=${stamp}$`));
		await expect(page.getByRole('list', { name: 'Results' })).toContainText(editedPerson);
	});

	await test.step('each search is sent once, typing is never lost, a slower older search never wins, and a failed one can be retried', async () => {
		const box = page.getByRole('searchbox', { name: 'Search movies and people' });
		const status = page.locator('#search-status');
		const results = page.getByRole('list', { name: 'Results' });

		// One request per search: keeping the address in step must not search again.
		const sent: string[] = [];
		const record = (request: Request) => {
			if (operationName(request) === 'Search') sent.push(request.postDataJSON()?.variables?.query);
		};
		page.on('request', record);
		const once = editedPerson.slice(0, -3);
		// The earlier results may already list the person, so wait for this search's own answer
		// and its address; a repeated request would have been sent before both.
		const answered = page.waitForResponse(
			(response) =>
				operationName(response.request()) === 'Search' &&
				response.request().postDataJSON()?.variables?.query === once
		);
		await box.fill(once);
		await answered;
		await expect(page).toHaveURL(`/search?q=${encodeURIComponent(once)}`);
		await expect(results).toContainText(editedPerson);
		page.off('request', record);
		expect(sent).toEqual([once]);

		// Typing at a steady pace, about the length of the pause before searching, keeps every key.
		await box.fill('');
		await box.pressSequentially(`Smoke ${stamp}`, { delay: 250 });
		await expect(box).toHaveValue(`Smoke ${stamp}`);

		// An older search is held, as on a slow connection; a newer one is answered meanwhile.
		let releaseStale!: () => void;
		const stale = new Promise<void>((resolve) => (releaseStale = resolve));
		let markAnswered!: () => void;
		const staleAnswered = new Promise<void>((resolve) => (markAnswered = resolve));
		await page.route('**/graphql', async (route) => {
			if (operationName(route.request()) !== 'Search') return route.fallback();
			if (route.request().postDataJSON()?.variables?.query !== 'Nobody stale')
				return route.fallback();
			await stale;
			try {
				await route.fulfill({
					json: {
						data: {
							search: [
								{ __typename: 'Person', id: crypto.randomUUID(), name: 'Stale Answer', credits: [] }
							]
						}
					}
				});
			} catch {
				// The page has abandoned this request, so it may no longer accept an answer.
			} finally {
				markAnswered();
			}
		});
		const held = page.waitForRequest(
			(request) =>
				operationName(request) === 'Search' &&
				request.postDataJSON()?.variables?.query === 'Nobody stale'
		);
		await box.fill('Nobody stale');
		const heldRequest = await held;
		const newer = page.waitForResponse(
			(response) =>
				operationName(response.request()) === 'Search' &&
				response.request().postDataJSON()?.variables?.query === once
		);
		await box.fill(once);
		await newer;
		releaseStale();
		await staleAnswered;
		// Once the browser is done with the old request and has drawn two frames, an answer it
		// accepted would be on screen.
		await heldRequest.response();
		await page.evaluate(
			() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
		);
		await expect(results.getByRole('link', { name: 'Stale Answer' })).toHaveCount(0);
		await expect(results).toContainText(editedPerson);
		await page.unrouteAll({ behavior: 'wait' });

		// A failed search explains itself and can be retried.
		let failed = false;
		await page.route('**/graphql', async (route) => {
			if (failed || operationName(route.request()) !== 'Search') return route.fallback();
			failed = true;
			await route.fulfill({
				json: {
					data: null,
					errors: [{ message: 'Search is down', extensions: { code: 'SERVICE_UNAVAILABLE' } }]
				}
			});
		});
		await box.fill('Nobody fails');
		// Nothing was being saved, so the message asks to try again.
		await expect(page.getByRole('alert')).toContainText('did not respond in time. Try again.');
		await page.unrouteAll({ behavior: 'wait' });
		await page.getByRole('button', { name: 'Retry' }).click();
		await expect(status).toContainText('Nothing matches “Nobody fails”.');
	});

	await test.step('a typed % is matched literally, and a search with no match says so', async () => {
		const box = page.getByRole('searchbox', { name: 'Search movies and people' });
		const status = page.locator('#search-status');
		// As a wildcard, % would match every name and title containing the stamp.
		await box.fill(`${stamp}%`);
		await expect(status).toContainText(`Nothing matches “${stamp}%”.`);
		await expect(page).toHaveURL(new RegExp(`/search\\?q=${stamp}%25$`));

		await box.fill('');
		await expect(status).toContainText("Type part of a movie title or a person's name.");
		await expect(page).toHaveURL(/\/search$/);

		await box.fill(String(stamp));
		await page
			.getByRole('list', { name: 'Results' })
			.getByRole('link', { name: creditedTitle })
			.click();
		await expect(page.getByRole('heading', { name: creditedTitle, level: 1 })).toBeVisible();
	});

	await test.step('the person page lists their credits, and deleting them is refused until those are removed', async () => {
		await page
			.getByRole('list', { name: 'Cast' })
			.getByRole('link', { name: editedPerson })
			.click();
		await expect(page.getByRole('heading', { name: editedPerson, level: 1 })).toBeVisible();
		const credits = page.getByRole('list', { name: 'Credits' });
		await expect(credits.getByRole('listitem')).toHaveCount(2);
		await expect(credits).toContainText('as Dr. Louise Banks');
		await expect(credits).toContainText('Director');

		await page.getByRole('button', { name: 'Delete', exact: true }).click();
		const dialog = page.getByRole('dialog', { name: `Delete ${editedPerson}?` });
		await expect(dialog).toContainText('is credited on a movie. Remove those credits first.');
		await dialog.getByRole('button', { name: 'Delete person' }).click();
		await expect(dialog.getByRole('alert')).toContainText('Remove their credits first');
		await dialog.getByRole('button', { name: 'Cancel' }).click();
		await expect(page.getByRole('heading', { name: editedPerson, level: 1 })).toBeVisible();
	});

	await test.step('removing credits asks first and keeps the person', async () => {
		await page
			.getByRole('list', { name: 'Credits' })
			.getByRole('link', { name: creditedTitle })
			.first()
			.click();
		await expect(page.getByRole('heading', { name: creditedTitle, level: 1 })).toBeVisible();

		await page.getByRole('button', { name: `Remove ${editedPerson} (Cast)` }).click();
		const dialog = page.getByRole('dialog', { name: `Remove ${editedPerson} from this movie?` });
		await expect(dialog).toContainText(`${editedPerson} stays in the catalog.`);
		await dialog.getByRole('button', { name: 'Remove credit' }).click();
		await expect(page.getByText('No cast yet.')).toBeVisible();
		// The removed row's button is gone, so focus goes to the section's own button.
		await expect(page.getByRole('button', { name: 'Add credit' })).toBeFocused();

		await page.getByRole('button', { name: `Remove ${editedPerson} (Director)` }).click();
		await page.getByRole('dialog').getByRole('button', { name: 'Remove credit' }).click();
		await expect(page.getByText('No creators yet.')).toBeVisible();

		// The movie was only needed for the credits.
		await page.getByRole('button', { name: 'Delete', exact: true }).click();
		await page.getByRole('dialog').getByRole('button', { name: 'Delete movie' }).click();
		await expect(page.getByText(`“${creditedTitle}” was deleted.`)).toBeVisible();

		await page.goto('/people');
		await page.getByRole('link', { name: new RegExp(editedPerson) }).click();
		await expect(page.getByText('No credits yet.')).toBeVisible();
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

/**
 * Opens the credit form while 100 other people sort first, checks that the first page of people
 * does not reach `name`, then finds and chooses them by typing part of the name.
 */
async function findBeyondTheFirstPage(page: Page, name: string) {
	await page.getByRole('button', { name: 'Add credit' }).click();
	// Opening the form moves focus into it.
	const search = page.getByRole('searchbox', { name: 'Person', exact: true });
	await expect(search).toBeFocused();
	await expect(search).toHaveAttribute('maxlength', '200');
	await expect(page.getByRole('radio').first()).toBeVisible();
	// The result count is announced with the rest of the search status.
	await expect(page.locator('#personId-status')).toContainText(/Showing 20 of \d+ people/);
	const matches = page.getByRole('radiogroup', { name: 'Person matching people' });
	await expect(matches).toHaveAttribute('aria-required', 'true');
	await expect(page.getByRole('radio', { name })).toHaveCount(0);
	await choosePerson(page, name);

	// A trailing space does not change the search, so nothing is sent again. Showing that
	// nothing is sent takes a wait longer than the form's 250 ms pause before searching.
	let searches = 0;
	const count = (request: Request) => {
		if (operationName(request) === 'PersonPicker') searches++;
	};
	page.on('request', count);
	await search.fill(`${name.slice(0, -3)} `);
	await page.waitForTimeout(600);
	page.off('request', count);
	expect(searches).toBe(0);
}

/** Finds a person in the credit form by typing part of their name, then chooses them. */
async function choosePerson(page: Page, name: string) {
	await page.getByRole('searchbox', { name: 'Person', exact: true }).fill(name.slice(0, -3));
	await page.getByRole('radio', { name, exact: true }).check();
}

/** Adds `count` people named "<prefix> 001", "<prefix> 002", ... through the API; returns their IDs. */
async function createPeople(page: Page, count: number, prefix: string): Promise<string[]> {
	const ids: string[] = [];
	for (let i = 1; i <= count; i++) {
		const response = await page.request.post('/graphql', {
			data: {
				query: 'mutation($name: String!) { createPerson(input: { name: $name }) { id } }',
				variables: { name: `${prefix} ${String(i).padStart(3, '0')}` }
			}
		});
		const body = await response.json();
		expect(body.errors, `creating ${prefix} ${i}`).toBeUndefined();
		ids.push(body.data.createPerson.id);
	}
	return ids;
}

/** Deletes people created by createPeople (each still at version 1). */
async function deletePeople(page: Page, ids: string[]) {
	for (const id of ids) {
		const response = await page.request.post('/graphql', {
			data: { query: `mutation { deletePerson(id: "${id}", version: 1) }` }
		});
		expect((await response.json()).data.deletePerson).toBe(id);
	}
}

/** Removes the movie's first credit in `list` through the API, as another browser tab or user would. */
async function removeCreditElsewhere(page: Page, list: 'cast' | 'creators') {
	const movieId = new URL(page.url()).pathname.split('/').pop();
	const credits = await page.request.post('/graphql', {
		data: { query: `{ movie(id: "${movieId}") { ${list} { id } } }` }
	});
	const id = (await credits.json()).data.movie[list][0].id;
	const removed = await page.request.post('/graphql', {
		data: { query: `mutation { removeCredit(id: "${id}") }` }
	});
	expect((await removed.json()).data.removeCredit).toBe(id);
}

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
