/* eslint-disable */
/** Internal type. DO NOT USE DIRECTLY. */
type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
/** Internal type. DO NOT USE DIRECTLY. */
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
import type { DocumentTypeDecoration } from '@graphql-typed-document-node/core';
export type MovieInput = {
  genres: Array<string>;
  releaseDate?: string | null | undefined;
  runtimeMinutes?: number | null | undefined;
  synopsis?: string | null | undefined;
  title: string;
};

/**
 * Offset pagination: offset >= 0 and 1 <= limit <= 100. Other values are a BAD_REQUEST error;
 * they are never silently adjusted, so the next page starts at offset + limit.
 * An explicit null limit means the default of 20.
 */
export type PageInput = {
  limit?: number | null | undefined;
  offset?: number | null | undefined;
};

export type PersonInput = {
  bio?: string | null | undefined;
  birthDate?: string | null | undefined;
  name: string;
};

export type MovieCardFieldsFragment = { id: string, title: string, releaseDate: string | null, genres: Array<string>, thumbnailUrl: string | null };

export type MoviesQueryVariables = Exact<{
  page?: PageInput | null | undefined;
}>;


export type MoviesQuery = { movies: { totalCount: number, offset: number, limit: number, items: Array<{ id: string, title: string, releaseDate: string | null, genres: Array<string>, thumbnailUrl: string | null }> } };

export type MovieDetailFieldsFragment = { id: string, title: string, synopsis: string | null, releaseDate: string | null, runtimeMinutes: number | null, genres: Array<string>, version: number, artworkUrl: string | null };

export type MovieQueryVariables = Exact<{
  id: string | number;
}>;


export type MovieQuery = { movie: { id: string, title: string, synopsis: string | null, releaseDate: string | null, runtimeMinutes: number | null, genres: Array<string>, version: number, artworkUrl: string | null } | null };

export type CreateMovieMutationVariables = Exact<{
  input: MovieInput;
}>;


export type CreateMovieMutation = { createMovie: { id: string } };

export type UpdateMovieMutationVariables = Exact<{
  id: string | number;
  version: number;
  input: MovieInput;
}>;


export type UpdateMovieMutation = { updateMovie: { id: string, title: string, synopsis: string | null, releaseDate: string | null, runtimeMinutes: number | null, genres: Array<string>, version: number, artworkUrl: string | null } };

export type DeleteMovieMutationVariables = Exact<{
  id: string | number;
  version: number;
}>;


export type DeleteMovieMutation = { deleteMovie: string };

export type RemoveArtworkMutationVariables = Exact<{
  movieId: string | number;
  version: number;
}>;


export type RemoveArtworkMutation = { removeArtwork: { id: string, title: string, synopsis: string | null, releaseDate: string | null, runtimeMinutes: number | null, genres: Array<string>, version: number, artworkUrl: string | null } };

export type UploadArtworkMutationVariables = Exact<{
  movieId: string | number;
  version: number;
  file: File;
}>;


export type UploadArtworkMutation = { uploadArtwork: { id: string, title: string, synopsis: string | null, releaseDate: string | null, runtimeMinutes: number | null, genres: Array<string>, version: number, artworkUrl: string | null } };

export type PeopleQueryVariables = Exact<{
  page?: PageInput | null | undefined;
}>;


export type PeopleQuery = { people: { totalCount: number, offset: number, limit: number, items: Array<{ id: string, name: string, birthDate: string | null }> } };

export type PersonDetailFieldsFragment = { id: string, name: string, bio: string | null, birthDate: string | null, version: number };

export type PersonQueryVariables = Exact<{
  id: string | number;
}>;


export type PersonQuery = { person: { id: string, name: string, bio: string | null, birthDate: string | null, version: number } | null };

export type CreatePersonMutationVariables = Exact<{
  input: PersonInput;
}>;


export type CreatePersonMutation = { createPerson: { id: string } };

export type UpdatePersonMutationVariables = Exact<{
  id: string | number;
  version: number;
  input: PersonInput;
}>;


export type UpdatePersonMutation = { updatePerson: { id: string, name: string, bio: string | null, birthDate: string | null, version: number } };

export type DeletePersonMutationVariables = Exact<{
  id: string | number;
  version: number;
}>;


export type DeletePersonMutation = { deletePerson: string };

export class TypedDocumentString<TResult, TVariables>
  extends String
  implements DocumentTypeDecoration<TResult, TVariables>
{
  __apiType?: NonNullable<DocumentTypeDecoration<TResult, TVariables>['__apiType']>;
  private value: string;
  public __meta__?: Record<string, any> | undefined;

  constructor(value: string, __meta__?: Record<string, any> | undefined) {
    super(value);
    this.value = value;
    this.__meta__ = __meta__;
  }

  override toString(): string & DocumentTypeDecoration<TResult, TVariables> {
    return this.value;
  }
}
export const MovieCardFieldsFragmentDoc = new TypedDocumentString(`
    fragment MovieCardFields on Movie {
  id
  title
  releaseDate
  genres
  thumbnailUrl: artworkUrl(variant: THUMBNAIL)
}
    `, {"fragmentName":"MovieCardFields"}) as unknown as TypedDocumentString<MovieCardFieldsFragment, unknown>;
export const MovieDetailFieldsFragmentDoc = new TypedDocumentString(`
    fragment MovieDetailFields on Movie {
  id
  title
  synopsis
  releaseDate
  runtimeMinutes
  genres
  version
  artworkUrl(variant: FULL)
}
    `, {"fragmentName":"MovieDetailFields"}) as unknown as TypedDocumentString<MovieDetailFieldsFragment, unknown>;
export const PersonDetailFieldsFragmentDoc = new TypedDocumentString(`
    fragment PersonDetailFields on Person {
  id
  name
  bio
  birthDate
  version
}
    `, {"fragmentName":"PersonDetailFields"}) as unknown as TypedDocumentString<PersonDetailFieldsFragment, unknown>;
export const MoviesDocument = new TypedDocumentString(`
    query Movies($page: PageInput) {
  movies(page: $page) {
    totalCount
    offset
    limit
    items {
      ...MovieCardFields
    }
  }
}
    fragment MovieCardFields on Movie {
  id
  title
  releaseDate
  genres
  thumbnailUrl: artworkUrl(variant: THUMBNAIL)
}`) as unknown as TypedDocumentString<MoviesQuery, MoviesQueryVariables>;
export const MovieDocument = new TypedDocumentString(`
    query Movie($id: ID!) {
  movie(id: $id) {
    ...MovieDetailFields
  }
}
    fragment MovieDetailFields on Movie {
  id
  title
  synopsis
  releaseDate
  runtimeMinutes
  genres
  version
  artworkUrl(variant: FULL)
}`) as unknown as TypedDocumentString<MovieQuery, MovieQueryVariables>;
export const CreateMovieDocument = new TypedDocumentString(`
    mutation CreateMovie($input: MovieInput!) {
  createMovie(input: $input) {
    id
  }
}
    `) as unknown as TypedDocumentString<CreateMovieMutation, CreateMovieMutationVariables>;
export const UpdateMovieDocument = new TypedDocumentString(`
    mutation UpdateMovie($id: ID!, $version: Int!, $input: MovieInput!) {
  updateMovie(id: $id, version: $version, input: $input) {
    ...MovieDetailFields
  }
}
    fragment MovieDetailFields on Movie {
  id
  title
  synopsis
  releaseDate
  runtimeMinutes
  genres
  version
  artworkUrl(variant: FULL)
}`) as unknown as TypedDocumentString<UpdateMovieMutation, UpdateMovieMutationVariables>;
export const DeleteMovieDocument = new TypedDocumentString(`
    mutation DeleteMovie($id: ID!, $version: Int!) {
  deleteMovie(id: $id, version: $version)
}
    `) as unknown as TypedDocumentString<DeleteMovieMutation, DeleteMovieMutationVariables>;
export const RemoveArtworkDocument = new TypedDocumentString(`
    mutation RemoveArtwork($movieId: ID!, $version: Int!) {
  removeArtwork(movieId: $movieId, version: $version) {
    ...MovieDetailFields
  }
}
    fragment MovieDetailFields on Movie {
  id
  title
  synopsis
  releaseDate
  runtimeMinutes
  genres
  version
  artworkUrl(variant: FULL)
}`) as unknown as TypedDocumentString<RemoveArtworkMutation, RemoveArtworkMutationVariables>;
export const UploadArtworkDocument = new TypedDocumentString(`
    mutation UploadArtwork($movieId: ID!, $version: Int!, $file: Upload!) {
  uploadArtwork(movieId: $movieId, version: $version, file: $file) {
    ...MovieDetailFields
  }
}
    fragment MovieDetailFields on Movie {
  id
  title
  synopsis
  releaseDate
  runtimeMinutes
  genres
  version
  artworkUrl(variant: FULL)
}`) as unknown as TypedDocumentString<UploadArtworkMutation, UploadArtworkMutationVariables>;
export const PeopleDocument = new TypedDocumentString(`
    query People($page: PageInput) {
  people(page: $page) {
    totalCount
    offset
    limit
    items {
      id
      name
      birthDate
    }
  }
}
    `) as unknown as TypedDocumentString<PeopleQuery, PeopleQueryVariables>;
export const PersonDocument = new TypedDocumentString(`
    query Person($id: ID!) {
  person(id: $id) {
    ...PersonDetailFields
  }
}
    fragment PersonDetailFields on Person {
  id
  name
  bio
  birthDate
  version
}`) as unknown as TypedDocumentString<PersonQuery, PersonQueryVariables>;
export const CreatePersonDocument = new TypedDocumentString(`
    mutation CreatePerson($input: PersonInput!) {
  createPerson(input: $input) {
    id
  }
}
    `) as unknown as TypedDocumentString<CreatePersonMutation, CreatePersonMutationVariables>;
export const UpdatePersonDocument = new TypedDocumentString(`
    mutation UpdatePerson($id: ID!, $version: Int!, $input: PersonInput!) {
  updatePerson(id: $id, version: $version, input: $input) {
    ...PersonDetailFields
  }
}
    fragment PersonDetailFields on Person {
  id
  name
  bio
  birthDate
  version
}`) as unknown as TypedDocumentString<UpdatePersonMutation, UpdatePersonMutationVariables>;
export const DeletePersonDocument = new TypedDocumentString(`
    mutation DeletePerson($id: ID!, $version: Int!) {
  deletePerson(id: $id, version: $version)
}
    `) as unknown as TypedDocumentString<DeletePersonMutation, DeletePersonMutationVariables>;