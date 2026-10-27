package com.moviecatalog.gateway.grpc

import com.moviecatalog.catalog.v1.ArtworkChangeResponse
import com.moviecatalog.catalog.v1.ClearArtworkRequest
import com.moviecatalog.catalog.v1.CreateMovieRequest
import com.moviecatalog.catalog.v1.CreatePersonRequest
import com.moviecatalog.catalog.v1.DeleteMovieRequest
import com.moviecatalog.catalog.v1.DeleteMovieResponse
import com.moviecatalog.catalog.v1.DeletePersonRequest
import com.moviecatalog.catalog.v1.GetMovieRequest
import com.moviecatalog.catalog.v1.GetPersonRequest
import com.moviecatalog.catalog.v1.ListMoviesRequest
import com.moviecatalog.catalog.v1.ListMoviesResponse
import com.moviecatalog.catalog.v1.ListPeopleRequest
import com.moviecatalog.catalog.v1.ListPeopleResponse
import com.moviecatalog.catalog.v1.Movie
import com.moviecatalog.catalog.v1.MovieFields
import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.catalog.v1.PageRequest
import com.moviecatalog.catalog.v1.Person
import com.moviecatalog.catalog.v1.PersonFields
import com.moviecatalog.catalog.v1.PersonServiceGrpc
import com.moviecatalog.catalog.v1.SetArtworkRequest
import com.moviecatalog.catalog.v1.UpdateMovieRequest
import com.moviecatalog.catalog.v1.UpdatePersonRequest
import com.moviecatalog.gateway.GatewayProperties
import io.grpc.stub.AbstractStub
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * The catalog calls the gateway makes, each with a deadline. Failures surface as
 * StatusRuntimeException and are turned into GraphQL errors by GrpcErrorResolver.
 */
@Component
class CatalogClient(
    private val movies: MovieServiceGrpc.MovieServiceBlockingStub,
    private val people: PersonServiceGrpc.PersonServiceBlockingStub,
    private val properties: GatewayProperties,
) {
    fun listMovies(offset: Int, limit: Int): ListMoviesResponse =
        movies.withDeadline().listMovies(ListMoviesRequest.newBuilder().setPage(page(offset, limit)).build())

    fun getMovie(id: String): Movie = movies.withDeadline().getMovie(GetMovieRequest.newBuilder().setId(id).build())

    fun createMovie(fields: MovieFields): Movie =
        movies.withDeadline().createMovie(CreateMovieRequest.newBuilder().setFields(fields).build())

    fun updateMovie(id: String, expectedVersion: Int, fields: MovieFields): Movie =
        movies.withDeadline().updateMovie(
            UpdateMovieRequest.newBuilder().setId(id).setExpectedVersion(expectedVersion).setFields(fields).build(),
        )

    fun deleteMovie(id: String, expectedVersion: Int): DeleteMovieResponse =
        movies.withDeadline().deleteMovie(DeleteMovieRequest.newBuilder().setId(id).setExpectedVersion(expectedVersion).build())

    fun clearArtwork(movieId: String, expectedVersion: Int): ArtworkChangeResponse =
        movies.withDeadline().clearArtwork(
            ClearArtworkRequest.newBuilder().setMovieId(movieId).setExpectedVersion(expectedVersion).build(),
        )

    fun setArtwork(movieId: String, expectedVersion: Int, artworkId: String): ArtworkChangeResponse =
        movies.withDeadline().setArtwork(
            SetArtworkRequest.newBuilder()
                .setMovieId(movieId)
                .setExpectedVersion(expectedVersion)
                .setArtworkId(artworkId)
                .build(),
        )

    fun listPeople(offset: Int, limit: Int): ListPeopleResponse =
        people.withDeadline().listPeople(ListPeopleRequest.newBuilder().setPage(page(offset, limit)).build())

    fun getPerson(id: String): Person = people.withDeadline().getPerson(GetPersonRequest.newBuilder().setId(id).build())

    fun createPerson(fields: PersonFields): Person =
        people.withDeadline().createPerson(CreatePersonRequest.newBuilder().setFields(fields).build())

    fun updatePerson(id: String, expectedVersion: Int, fields: PersonFields): Person =
        people.withDeadline().updatePerson(
            UpdatePersonRequest.newBuilder().setId(id).setExpectedVersion(expectedVersion).setFields(fields).build(),
        )

    fun deletePerson(id: String, expectedVersion: Int): String =
        people.withDeadline().deletePerson(DeletePersonRequest.newBuilder().setId(id).setExpectedVersion(expectedVersion).build()).id

    private fun page(offset: Int, limit: Int) = PageRequest.newBuilder().setOffset(offset).setLimit(limit)

    private fun <S : AbstractStub<S>> S.withDeadline(): S =
        withDeadlineAfter(properties.catalogDeadline.toMillis(), TimeUnit.MILLISECONDS)
}
