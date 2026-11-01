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
    "\n\tquery Movie($id: ID!) {\n\t\tmovie(id: $id) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t\ttmdbPostersEnabled\n\t}\n": typeof types.MovieDocument,
    "\n\tquery FindTmdbPoster($movieId: ID!) {\n\t\ttmdbPoster(movieId: $movieId) {\n\t\t\ttmdbId\n\t\t\ttitle\n\t\t\treleaseYear\n\t\t\tpreviewUrl\n\t\t}\n\t}\n": typeof types.FindTmdbPosterDocument,
    "\n\tmutation UseTmdbPoster($movieId: ID!, $version: Int!, $tmdbId: Int!) {\n\t\tuseTmdbPoster(movieId: $movieId, version: $version, tmdbId: $tmdbId) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n": typeof types.UseTmdbPosterDocument,
    "\n\tmutation CreateMovie($input: MovieInput!) {\n\t\tcreateMovie(input: $input) {\n\t\t\tid\n\t\t}\n\t}\n": typeof types.CreateMovieDocument,
    "\n\tmutation UpdateMovie($id: ID!, $version: Int!, $input: MovieInput!) {\n\t\tupdateMovie(id: $id, version: $version, input: $input) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n": typeof types.UpdateMovieDocument,
    "\n\tmutation DeleteMovie($id: ID!, $version: Int!) {\n\t\tdeleteMovie(id: $id, version: $version)\n\t}\n": typeof types.DeleteMovieDocument,
    "\n\tmutation RemoveArtwork($movieId: ID!, $version: Int!) {\n\t\tremoveArtwork(movieId: $movieId, version: $version) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n": typeof types.RemoveArtworkDocument,
    "\n\tmutation UploadArtwork($movieId: ID!, $version: Int!, $file: Upload!) {\n\t\tuploadArtwork(movieId: $movieId, version: $version, file: $file) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n": typeof types.UploadArtworkDocument,
    "\n\tquery People($page: PageInput) {\n\t\tpeople(page: $page) {\n\t\t\ttotalCount\n\t\t\toffset\n\t\t\tlimit\n\t\t\titems {\n\t\t\t\tid\n\t\t\t\tname\n\t\t\t\tbirthDate\n\t\t\t}\n\t\t}\n\t}\n": typeof types.PeopleDocument,
    "\n\tfragment PersonDetailFields on Person {\n\t\tid\n\t\tname\n\t\tbio\n\t\tbirthDate\n\t\tversion\n\t}\n": typeof types.PersonDetailFieldsFragmentDoc,
    "\n\tquery Person($id: ID!) {\n\t\tperson(id: $id) {\n\t\t\t...PersonDetailFields\n\t\t\tcredits {\n\t\t\t\tid\n\t\t\t\ttype\n\t\t\t\tcharacterName\n\t\t\t\tmovie {\n\t\t\t\t\tid\n\t\t\t\t\ttitle\n\t\t\t\t\treleaseDate\n\t\t\t\t}\n\t\t\t}\n\t\t}\n\t}\n": typeof types.PersonDocument,
    "\n\tmutation CreatePerson($input: PersonInput!) {\n\t\tcreatePerson(input: $input) {\n\t\t\tid\n\t\t}\n\t}\n": typeof types.CreatePersonDocument,
    "\n\tmutation UpdatePerson($id: ID!, $version: Int!, $input: PersonInput!) {\n\t\tupdatePerson(id: $id, version: $version, input: $input) {\n\t\t\t...PersonDetailFields\n\t\t}\n\t}\n": typeof types.UpdatePersonDocument,
    "\n\tmutation DeletePerson($id: ID!, $version: Int!) {\n\t\tdeletePerson(id: $id, version: $version)\n\t}\n": typeof types.DeletePersonDocument,
    "\n\tfragment CreditFields on Credit {\n\t\tid\n\t\ttype\n\t\tcharacterName\n\t\tbillingOrder\n\t\tperson {\n\t\t\tid\n\t\t\tname\n\t\t}\n\t}\n": typeof types.CreditFieldsFragmentDoc,
    "\n\tquery MovieCredits($id: ID!) {\n\t\tmovie(id: $id) {\n\t\t\tid\n\t\t\tcast {\n\t\t\t\t...CreditFields\n\t\t\t}\n\t\t\tcreators {\n\t\t\t\t...CreditFields\n\t\t\t}\n\t\t}\n\t}\n": typeof types.MovieCreditsDocument,
    "\n\tquery PersonPicker($nameContains: String) {\n\t\tpeople(nameContains: $nameContains) {\n\t\t\ttotalCount\n\t\t\titems {\n\t\t\t\tid\n\t\t\t\tname\n\t\t\t}\n\t\t}\n\t}\n": typeof types.PersonPickerDocument,
    "\n\tmutation AddCredit($input: AddCreditInput!) {\n\t\taddCredit(input: $input) {\n\t\t\t...CreditFields\n\t\t}\n\t}\n": typeof types.AddCreditDocument,
    "\n\tmutation UpdateCredit($id: ID!, $input: UpdateCreditInput!) {\n\t\tupdateCredit(id: $id, input: $input) {\n\t\t\t...CreditFields\n\t\t}\n\t}\n": typeof types.UpdateCreditDocument,
    "\n\tmutation RemoveCredit($id: ID!) {\n\t\tremoveCredit(id: $id)\n\t}\n": typeof types.RemoveCreditDocument,
    "\n\tquery Search($query: String!, $limit: Int) {\n\t\tsearch(query: $query, limit: $limit) {\n\t\t\t__typename\n\t\t\t... on Movie {\n\t\t\t\tid\n\t\t\t\ttitle\n\t\t\t\treleaseDate\n\t\t\t}\n\t\t\t... on Person {\n\t\t\t\tid\n\t\t\t\tname\n\t\t\t\tcredits {\n\t\t\t\t\ttype\n\t\t\t\t\tmovie {\n\t\t\t\t\t\tid\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n\t\t}\n\t}\n": typeof types.SearchDocument,
};
const documents: Documents = {
    "\n\tfragment MovieCardFields on Movie {\n\t\tid\n\t\ttitle\n\t\treleaseDate\n\t\tgenres\n\t\tthumbnailUrl: artworkUrl(variant: THUMBNAIL)\n\t}\n": types.MovieCardFieldsFragmentDoc,
    "\n\tquery Movies($page: PageInput) {\n\t\tmovies(page: $page) {\n\t\t\ttotalCount\n\t\t\toffset\n\t\t\tlimit\n\t\t\titems {\n\t\t\t\t...MovieCardFields\n\t\t\t}\n\t\t}\n\t}\n": types.MoviesDocument,
    "\n\tfragment MovieDetailFields on Movie {\n\t\tid\n\t\ttitle\n\t\tsynopsis\n\t\treleaseDate\n\t\truntimeMinutes\n\t\tgenres\n\t\tversion\n\t\tartworkUrl(variant: FULL)\n\t}\n": types.MovieDetailFieldsFragmentDoc,
    "\n\tquery Movie($id: ID!) {\n\t\tmovie(id: $id) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t\ttmdbPostersEnabled\n\t}\n": types.MovieDocument,
    "\n\tquery FindTmdbPoster($movieId: ID!) {\n\t\ttmdbPoster(movieId: $movieId) {\n\t\t\ttmdbId\n\t\t\ttitle\n\t\t\treleaseYear\n\t\t\tpreviewUrl\n\t\t}\n\t}\n": types.FindTmdbPosterDocument,
    "\n\tmutation UseTmdbPoster($movieId: ID!, $version: Int!, $tmdbId: Int!) {\n\t\tuseTmdbPoster(movieId: $movieId, version: $version, tmdbId: $tmdbId) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n": types.UseTmdbPosterDocument,
    "\n\tmutation CreateMovie($input: MovieInput!) {\n\t\tcreateMovie(input: $input) {\n\t\t\tid\n\t\t}\n\t}\n": types.CreateMovieDocument,
    "\n\tmutation UpdateMovie($id: ID!, $version: Int!, $input: MovieInput!) {\n\t\tupdateMovie(id: $id, version: $version, input: $input) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n": types.UpdateMovieDocument,
    "\n\tmutation DeleteMovie($id: ID!, $version: Int!) {\n\t\tdeleteMovie(id: $id, version: $version)\n\t}\n": types.DeleteMovieDocument,
    "\n\tmutation RemoveArtwork($movieId: ID!, $version: Int!) {\n\t\tremoveArtwork(movieId: $movieId, version: $version) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n": types.RemoveArtworkDocument,
    "\n\tmutation UploadArtwork($movieId: ID!, $version: Int!, $file: Upload!) {\n\t\tuploadArtwork(movieId: $movieId, version: $version, file: $file) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n": types.UploadArtworkDocument,
    "\n\tquery People($page: PageInput) {\n\t\tpeople(page: $page) {\n\t\t\ttotalCount\n\t\t\toffset\n\t\t\tlimit\n\t\t\titems {\n\t\t\t\tid\n\t\t\t\tname\n\t\t\t\tbirthDate\n\t\t\t}\n\t\t}\n\t}\n": types.PeopleDocument,
    "\n\tfragment PersonDetailFields on Person {\n\t\tid\n\t\tname\n\t\tbio\n\t\tbirthDate\n\t\tversion\n\t}\n": types.PersonDetailFieldsFragmentDoc,
    "\n\tquery Person($id: ID!) {\n\t\tperson(id: $id) {\n\t\t\t...PersonDetailFields\n\t\t\tcredits {\n\t\t\t\tid\n\t\t\t\ttype\n\t\t\t\tcharacterName\n\t\t\t\tmovie {\n\t\t\t\t\tid\n\t\t\t\t\ttitle\n\t\t\t\t\treleaseDate\n\t\t\t\t}\n\t\t\t}\n\t\t}\n\t}\n": types.PersonDocument,
    "\n\tmutation CreatePerson($input: PersonInput!) {\n\t\tcreatePerson(input: $input) {\n\t\t\tid\n\t\t}\n\t}\n": types.CreatePersonDocument,
    "\n\tmutation UpdatePerson($id: ID!, $version: Int!, $input: PersonInput!) {\n\t\tupdatePerson(id: $id, version: $version, input: $input) {\n\t\t\t...PersonDetailFields\n\t\t}\n\t}\n": types.UpdatePersonDocument,
    "\n\tmutation DeletePerson($id: ID!, $version: Int!) {\n\t\tdeletePerson(id: $id, version: $version)\n\t}\n": types.DeletePersonDocument,
    "\n\tfragment CreditFields on Credit {\n\t\tid\n\t\ttype\n\t\tcharacterName\n\t\tbillingOrder\n\t\tperson {\n\t\t\tid\n\t\t\tname\n\t\t}\n\t}\n": types.CreditFieldsFragmentDoc,
    "\n\tquery MovieCredits($id: ID!) {\n\t\tmovie(id: $id) {\n\t\t\tid\n\t\t\tcast {\n\t\t\t\t...CreditFields\n\t\t\t}\n\t\t\tcreators {\n\t\t\t\t...CreditFields\n\t\t\t}\n\t\t}\n\t}\n": types.MovieCreditsDocument,
    "\n\tquery PersonPicker($nameContains: String) {\n\t\tpeople(nameContains: $nameContains) {\n\t\t\ttotalCount\n\t\t\titems {\n\t\t\t\tid\n\t\t\t\tname\n\t\t\t}\n\t\t}\n\t}\n": types.PersonPickerDocument,
    "\n\tmutation AddCredit($input: AddCreditInput!) {\n\t\taddCredit(input: $input) {\n\t\t\t...CreditFields\n\t\t}\n\t}\n": types.AddCreditDocument,
    "\n\tmutation UpdateCredit($id: ID!, $input: UpdateCreditInput!) {\n\t\tupdateCredit(id: $id, input: $input) {\n\t\t\t...CreditFields\n\t\t}\n\t}\n": types.UpdateCreditDocument,
    "\n\tmutation RemoveCredit($id: ID!) {\n\t\tremoveCredit(id: $id)\n\t}\n": types.RemoveCreditDocument,
    "\n\tquery Search($query: String!, $limit: Int) {\n\t\tsearch(query: $query, limit: $limit) {\n\t\t\t__typename\n\t\t\t... on Movie {\n\t\t\t\tid\n\t\t\t\ttitle\n\t\t\t\treleaseDate\n\t\t\t}\n\t\t\t... on Person {\n\t\t\t\tid\n\t\t\t\tname\n\t\t\t\tcredits {\n\t\t\t\t\ttype\n\t\t\t\t\tmovie {\n\t\t\t\t\t\tid\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n\t\t}\n\t}\n": types.SearchDocument,
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
export function graphql(source: "\n\tquery Movie($id: ID!) {\n\t\tmovie(id: $id) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t\ttmdbPostersEnabled\n\t}\n"): typeof import('./graphql').MovieDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tquery FindTmdbPoster($movieId: ID!) {\n\t\ttmdbPoster(movieId: $movieId) {\n\t\t\ttmdbId\n\t\t\ttitle\n\t\t\treleaseYear\n\t\t\tpreviewUrl\n\t\t}\n\t}\n"): typeof import('./graphql').FindTmdbPosterDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation UseTmdbPoster($movieId: ID!, $version: Int!, $tmdbId: Int!) {\n\t\tuseTmdbPoster(movieId: $movieId, version: $version, tmdbId: $tmdbId) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n"): typeof import('./graphql').UseTmdbPosterDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation CreateMovie($input: MovieInput!) {\n\t\tcreateMovie(input: $input) {\n\t\t\tid\n\t\t}\n\t}\n"): typeof import('./graphql').CreateMovieDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation UpdateMovie($id: ID!, $version: Int!, $input: MovieInput!) {\n\t\tupdateMovie(id: $id, version: $version, input: $input) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n"): typeof import('./graphql').UpdateMovieDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation DeleteMovie($id: ID!, $version: Int!) {\n\t\tdeleteMovie(id: $id, version: $version)\n\t}\n"): typeof import('./graphql').DeleteMovieDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation RemoveArtwork($movieId: ID!, $version: Int!) {\n\t\tremoveArtwork(movieId: $movieId, version: $version) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n"): typeof import('./graphql').RemoveArtworkDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation UploadArtwork($movieId: ID!, $version: Int!, $file: Upload!) {\n\t\tuploadArtwork(movieId: $movieId, version: $version, file: $file) {\n\t\t\t...MovieDetailFields\n\t\t}\n\t}\n"): typeof import('./graphql').UploadArtworkDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tquery People($page: PageInput) {\n\t\tpeople(page: $page) {\n\t\t\ttotalCount\n\t\t\toffset\n\t\t\tlimit\n\t\t\titems {\n\t\t\t\tid\n\t\t\t\tname\n\t\t\t\tbirthDate\n\t\t\t}\n\t\t}\n\t}\n"): typeof import('./graphql').PeopleDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tfragment PersonDetailFields on Person {\n\t\tid\n\t\tname\n\t\tbio\n\t\tbirthDate\n\t\tversion\n\t}\n"): typeof import('./graphql').PersonDetailFieldsFragmentDoc;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tquery Person($id: ID!) {\n\t\tperson(id: $id) {\n\t\t\t...PersonDetailFields\n\t\t\tcredits {\n\t\t\t\tid\n\t\t\t\ttype\n\t\t\t\tcharacterName\n\t\t\t\tmovie {\n\t\t\t\t\tid\n\t\t\t\t\ttitle\n\t\t\t\t\treleaseDate\n\t\t\t\t}\n\t\t\t}\n\t\t}\n\t}\n"): typeof import('./graphql').PersonDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation CreatePerson($input: PersonInput!) {\n\t\tcreatePerson(input: $input) {\n\t\t\tid\n\t\t}\n\t}\n"): typeof import('./graphql').CreatePersonDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation UpdatePerson($id: ID!, $version: Int!, $input: PersonInput!) {\n\t\tupdatePerson(id: $id, version: $version, input: $input) {\n\t\t\t...PersonDetailFields\n\t\t}\n\t}\n"): typeof import('./graphql').UpdatePersonDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation DeletePerson($id: ID!, $version: Int!) {\n\t\tdeletePerson(id: $id, version: $version)\n\t}\n"): typeof import('./graphql').DeletePersonDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tfragment CreditFields on Credit {\n\t\tid\n\t\ttype\n\t\tcharacterName\n\t\tbillingOrder\n\t\tperson {\n\t\t\tid\n\t\t\tname\n\t\t}\n\t}\n"): typeof import('./graphql').CreditFieldsFragmentDoc;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tquery MovieCredits($id: ID!) {\n\t\tmovie(id: $id) {\n\t\t\tid\n\t\t\tcast {\n\t\t\t\t...CreditFields\n\t\t\t}\n\t\t\tcreators {\n\t\t\t\t...CreditFields\n\t\t\t}\n\t\t}\n\t}\n"): typeof import('./graphql').MovieCreditsDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tquery PersonPicker($nameContains: String) {\n\t\tpeople(nameContains: $nameContains) {\n\t\t\ttotalCount\n\t\t\titems {\n\t\t\t\tid\n\t\t\t\tname\n\t\t\t}\n\t\t}\n\t}\n"): typeof import('./graphql').PersonPickerDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation AddCredit($input: AddCreditInput!) {\n\t\taddCredit(input: $input) {\n\t\t\t...CreditFields\n\t\t}\n\t}\n"): typeof import('./graphql').AddCreditDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation UpdateCredit($id: ID!, $input: UpdateCreditInput!) {\n\t\tupdateCredit(id: $id, input: $input) {\n\t\t\t...CreditFields\n\t\t}\n\t}\n"): typeof import('./graphql').UpdateCreditDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tmutation RemoveCredit($id: ID!) {\n\t\tremoveCredit(id: $id)\n\t}\n"): typeof import('./graphql').RemoveCreditDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n\tquery Search($query: String!, $limit: Int) {\n\t\tsearch(query: $query, limit: $limit) {\n\t\t\t__typename\n\t\t\t... on Movie {\n\t\t\t\tid\n\t\t\t\ttitle\n\t\t\t\treleaseDate\n\t\t\t}\n\t\t\t... on Person {\n\t\t\t\tid\n\t\t\t\tname\n\t\t\t\tcredits {\n\t\t\t\t\ttype\n\t\t\t\t\tmovie {\n\t\t\t\t\t\tid\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n\t\t}\n\t}\n"): typeof import('./graphql').SearchDocument;


export function graphql(source: string) {
  return (documents as any)[source] ?? {};
}
