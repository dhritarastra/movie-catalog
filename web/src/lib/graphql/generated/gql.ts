/* eslint-disable */
import * as types from './graphql';



/**
 * Map of all GraphQL operations in the project.
 *
 * This map has several performance disadvantages:
 * 1. It is not tree-shakeable, so it will include all operations in the project.
 * 2. It is not minifiable, so the string of a GraphQL query will be multiple times inside the bundle.
 * 3. It does not support dead code elimination, so it will add unused operations.
 *
 * Therefore it is highly recommended to use the babel or swc plugin for production.
 * Learn more about it here: https://the-guild.dev/graphql/codegen/plugins/presets/preset-client#reducing-bundle-size
 */
type Documents = {
    "\n\tfragment MovieCardFields on Movie {\n\t\tid\n\t\ttitle\n\t\treleaseDate\n\t\tgenres\n\t\tthumbnailUrl: artworkUrl(variant: THUMBNAIL)\n\t}\n": typeof types.MovieCardFieldsFragmentDoc,
    "\n\tquery Movies($page: PageInput) {\n\t\tmovies(page: $page) {\n\t\t\ttotalCount\n\t\t\toffset\n\t\t\tlimit\n\t\t\titems {\n\t\t\t\t...MovieCardFields\n\t\t\t}\n\t\t}\n\t}\n": typeof types.MoviesDocument,
    "\n\tfragment MovieDetailFields on Movie {\n\t\tid\n\t\ttitle\n\t\tsynopsis\n\t\treleaseDate\n\t\truntimeMinutes\n\t\tgenres\n\t\tversion\n\t\tartworkUrl(variant: FULL)\n\t}\n": typeof types.MovieDetailFieldsFragmentDoc,
    "\n\tquery Movie($id: ID!) {\n\t\tmovie(id: $id) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n": typeof types.MovieDocument,
    "\n\tmutation CreateMovie($input: MovieInput!) {\n\t\tcreateMovie(input: $input) {\n\t\t\tid\n\t\t}\n\t}\n": typeof types.CreateMovieDocument,
    "\n\tmutation UploadArtwork($movieId: ID!, $version: Int!, $file: Upload!) {\n\t\tuploadArtwork(movieId: $movieId, version: $version, file: $file) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n": typeof types.UploadArtworkDocument,
};
const documents: Documents = {
    "\n\tfragment MovieCardFields on Movie {\n\t\tid\n\t\ttitle\n\t\treleaseDate\n\t\tgenres\n\t\tthumbnailUrl: artworkUrl(variant: THUMBNAIL)\n\t}\n": types.MovieCardFieldsFragmentDoc,
    "\n\tquery Movies($page: PageInput) {\n\t\tmovies(page: $page) {\n\t\t\ttotalCount\n\t\t\toffset\n\t\t\tlimit\n\t\t\titems {\n\t\t\t\t...MovieCardFields\n\t\t\t}\n\t\t}\n\t}\n": types.MoviesDocument,
    "\n\tfragment MovieDetailFields on Movie {\n\t\tid\n\t\ttitle\n\t\tsynopsis\n\t\treleaseDate\n\t\truntimeMinutes\n\t\tgenres\n\t\tversion\n\t\tartworkUrl(variant: FULL)\n\t}\n": types.MovieDetailFieldsFragmentDoc,
    "\n\tquery Movie($id: ID!) {\n\t\tmovie(id: $id) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n": types.MovieDocument,
    "\n\tmutation CreateMovie($input: MovieInput!) {\n\t\tcreateMovie(input: $input) {\n\t\t\tid\n\t\t}\n\t}\n": types.CreateMovieDocument,
    "\n\tmutation UploadArtwork($movieId: ID!, $version: Int!, $file: Upload!) {\n\t\tuploadArtwork(movieId: $movieId, version: $version, file: $file) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n": types.UploadArtworkDocument,
};

/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tfragment MovieCardFields on Movie {\n\t\tid\n\t\ttitle\n\t\treleaseDate\n\t\tgenres\n\t\tthumbnailUrl: artworkUrl(variant: THUMBNAIL)\n\t}\n"): typeof import('./graphql').MovieCardFieldsFragmentDoc;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tquery Movies($page: PageInput) {\n\t\tmovies(page: $page) {\n\t\t\ttotalCount\n\t\t\toffset\n\t\t\tlimit\n\t\t\titems {\n\t\t\t\t...MovieCardFields\n\t\t\t}\n\t\t}\n\t}\n"): typeof import('./graphql').MoviesDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tfragment MovieDetailFields on Movie {\n\t\tid\n\t\ttitle\n\t\tsynopsis\n\t\treleaseDate\n\t\truntimeMinutes\n\t\tgenres\n\t\tversion\n\t\tartworkUrl(variant: FULL)\n\t}\n"): typeof import('./graphql').MovieDetailFieldsFragmentDoc;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tquery Movie($id: ID!) {\n\t\tmovie(id: $id) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n"): typeof import('./graphql').MovieDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation CreateMovie($input: MovieInput!) {\n\t\tcreateMovie(input: $input) {\n\t\t\tid\n\t\t}\n\t}\n"): typeof import('./graphql').CreateMovieDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation UploadArtwork($movieId: ID!, $version: Int!, $file: Upload!) {\n\t\tuploadArtwork(movieId: $movieId, version: $version, file: $file) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n"): typeof import('./graphql').UploadArtworkDocument;


export function graphql(source: string) {
  return (documents as any)[source] ?? {};
}
