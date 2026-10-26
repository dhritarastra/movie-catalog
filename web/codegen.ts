import type { CodegenConfig } from '@graphql-codegen/cli';

// Generates TypeScript types for every operation in src/lib/graphql/operations.ts from the
// gateway's checked-in schema, so a schema change that breaks the web app fails `npm run check`.
const config: CodegenConfig = {
	schema: '../services/gateway/src/main/resources/graphql/schema.graphqls',
	documents: ['src/lib/graphql/operations.ts'],
	generates: {
		'./src/lib/graphql/generated/': {
			preset: 'client',
			presetConfig: { fragmentMasking: false },
			config: {
				// Operations become typed strings, so the app needs no GraphQL runtime library.
				documentMode: 'string',
				useTypeImports: true,
				enumsAsTypes: true,
				scalars: { Upload: 'File' }
			}
		}
	}
};

export default config;
