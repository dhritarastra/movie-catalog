import { graphQlApi, seedCatalog, SeedError } from './seed.ts';

// Run with `npm run seed` against the Docker stack, or `docker compose run --rm seed`.
// SEED_URL points it elsewhere, such as the gateway on port 8080 during local development.
const url = process.env.SEED_URL ?? 'http://localhost:3000/graphql';

try {
	console.log(`Seeding the catalog at ${url}`);
	await seedCatalog(graphQlApi(url), (line) => console.log(line));
	console.log('Done.');
} catch (error) {
	console.error(error instanceof SeedError ? error.message : error);
	process.exitCode = 1;
}
