import { graphql } from './generated';

// Every GraphQL operation the app sends. `npm run codegen` turns each into a typed document.

export const MovieCardFields = graphql(`
	fragment MovieCardFields on Movie {
		id
		title
		releaseDate
		genres
		thumbnailUrl: artworkUrl(variant: THUMBNAIL)
	}
`);

export const MoviesQuery = graphql(`
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
`);

export const MovieDetailFields = graphql(`
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
`);

export const MovieQuery = graphql(`
	query Movie($id: ID!) {
		movie(id: $id) {
			...MovieDetailFields
		}
	}
`);

export const CreateMovieMutation = graphql(`
	mutation CreateMovie($input: MovieInput!) {
		createMovie(input: $input) {
			id
		}
	}
`);

export const UpdateMovieMutation = graphql(`
	mutation UpdateMovie($id: ID!, $version: Int!, $input: MovieInput!) {
		updateMovie(id: $id, version: $version, input: $input) {
			...MovieDetailFields
		}
	}
`);

export const DeleteMovieMutation = graphql(`
	mutation DeleteMovie($id: ID!, $version: Int!) {
		deleteMovie(id: $id, version: $version)
	}
`);

export const RemoveArtworkMutation = graphql(`
	mutation RemoveArtwork($movieId: ID!, $version: Int!) {
		removeArtwork(movieId: $movieId, version: $version) {
			...MovieDetailFields
		}
	}
`);

export const UploadArtworkMutation = graphql(`
	mutation UploadArtwork($movieId: ID!, $version: Int!, $file: Upload!) {
		uploadArtwork(movieId: $movieId, version: $version, file: $file) {
			...MovieDetailFields
		}
	}
`);
