import type { CodegenConfig } from '@graphql-codegen/cli';

// Generates types for the operations in operations.ts from the gateway schema.
const config: CodegenConfig = {
	schema: '../services/gateway/src/main/resources/graphql/schema.graphqls',
	documents: ['src/lib/graphql/operations.ts'],
	generates: {
		'./src/lib/graphql/generated/': {
			preset: 'client',
			presetConfig: { fragmentMasking: false },
			config: {
				// Typed strings, so no GraphQL client library is needed.
				documentMode: 'string',
				useTypeImports: true,
				enumsAsTypes: true,
				scalars: { Upload: 'File' }
			}
		}
	}
};

export default config;
