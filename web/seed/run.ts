import { graphQlApi, seedCatalog, SeedError, sharedPoster, tmdbPosters } from './seed.ts';

// Usage: npm run seed. SEED_URL points it at another server; TMDB_API_TOKEN adds real posters.
const url = process.env.SEED_URL ?? 'http://localhost:3000/graphql';

try {
	console.log(`Seeding the catalog at ${url}`);
	const token = process.env.TMDB_API_TOKEN;
	console.log(
		token
			? "Fetching each movie's poster from TMDB."
			: 'TMDB_API_TOKEN is not set, so every movie gets the same shared poster.'
	);
	await seedCatalog(graphQlApi(url), (line) => console.log(line), {
		posters: token ? tmdbPosters(token) : sharedPoster
	});
	console.log('Done.');
} catch (error) {
	console.error(error instanceof SeedError ? error.message : error);
	process.exitCode = 1;
}
