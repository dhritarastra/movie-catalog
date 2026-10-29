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

export const PeopleQuery = graphql(`
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
`);

export const PersonDetailFields = graphql(`
	fragment PersonDetailFields on Person {
		id
		name
		bio
		birthDate
		version
	}
`);

export const PersonQuery = graphql(`
	query Person($id: ID!) {
		person(id: $id) {
			...PersonDetailFields
			credits {
				id
				type
				characterName
				movie {
					id
					title
					releaseDate
				}
			}
		}
	}
`);

export const CreatePersonMutation = graphql(`
	mutation CreatePerson($input: PersonInput!) {
		createPerson(input: $input) {
			id
		}
	}
`);

export const UpdatePersonMutation = graphql(`
	mutation UpdatePerson($id: ID!, $version: Int!, $input: PersonInput!) {
		updatePerson(id: $id, version: $version, input: $input) {
			...PersonDetailFields
		}
	}
`);

export const DeletePersonMutation = graphql(`
	mutation DeletePerson($id: ID!, $version: Int!) {
		deletePerson(id: $id, version: $version)
	}
`);

export const CreditFields = graphql(`
	fragment CreditFields on Credit {
		id
		type
		characterName
		billingOrder
		person {
			id
			name
		}
	}
`);

export const MovieCreditsQuery = graphql(`
	query MovieCredits($id: ID!) {
		movie(id: $id) {
			id
			cast {
				...CreditFields
			}
			creators {
				...CreditFields
			}
		}
	}
`);

/**
 * The people offered when crediting someone: alphabetical, up to the API's page maximum. 100 is
 * catalog.paging.max-limit in the catalog's application.yml; lowering that below 100 would make
 * this query fail. The picker becomes a search when search arrives.
 */
export const PersonPickerQuery = graphql(`
	query PersonPicker {
		people(page: { limit: 100 }) {
			totalCount
			items {
				id
				name
			}
		}
	}
`);

export const AddCreditMutation = graphql(`
	mutation AddCredit($input: AddCreditInput!) {
		addCredit(input: $input) {
			...CreditFields
		}
	}
`);

export const UpdateCreditMutation = graphql(`
	mutation UpdateCredit($id: ID!, $input: UpdateCreditInput!) {
		updateCredit(id: $id, input: $input) {
			...CreditFields
		}
	}
`);

export const RemoveCreditMutation = graphql(`
	mutation RemoveCredit($id: ID!) {
		removeCredit(id: $id)
	}
`);
