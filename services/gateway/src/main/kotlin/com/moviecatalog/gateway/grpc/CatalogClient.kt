package com.moviecatalog.gateway.grpc

import com.moviecatalog.catalog.v1.AddCreditRequest
import com.moviecatalog.catalog.v1.ArtworkChangeResponse
import com.moviecatalog.catalog.v1.ClearArtworkRequest
import com.moviecatalog.catalog.v1.CreateMovieRequest
import com.moviecatalog.catalog.v1.CreatePersonRequest
import com.moviecatalog.catalog.v1.Credit
import com.moviecatalog.catalog.v1.CreditServiceGrpc
import com.moviecatalog.catalog.v1.DeleteMovieRequest
import com.moviecatalog.catalog.v1.DeleteMovieResponse
import com.moviecatalog.catalog.v1.DeletePersonRequest
import com.moviecatalog.catalog.v1.GetCreditsByMovieIdsRequest
import com.moviecatalog.catalog.v1.GetCreditsByPersonIdsRequest
import com.moviecatalog.catalog.v1.GetMovieRequest
import com.moviecatalog.catalog.v1.GetMoviesByIdsRequest
import com.moviecatalog.catalog.v1.GetPeopleByIdsRequest
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
import com.moviecatalog.catalog.v1.RemoveCreditRequest
import com.moviecatalog.catalog.v1.SearchHit
import com.moviecatalog.catalog.v1.SearchRequest
import com.moviecatalog.catalog.v1.SearchServiceGrpc
import com.moviecatalog.catalog.v1.SetArtworkRequest
import com.moviecatalog.catalog.v1.UpdateCreditRequest
import com.moviecatalog.catalog.v1.UpdateMovieRequest
import com.moviecatalog.catalog.v1.UpdatePersonRequest
import com.moviecatalog.gateway.config.GatewayProperties
import com.moviecatalog.gateway.credit.CreditType
import io.grpc.stub.AbstractStub
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import com.moviecatalog.catalog.v1.CreditType as CreditTypeProto

/**
 * The catalog calls the gateway makes, each with a deadline. Failures surface as
 * StatusRuntimeException and are turned into GraphQL errors by GrpcErrorResolver.
 */
@Component
class CatalogClient(
    private val movies: MovieServiceGrpc.MovieServiceBlockingStub,
    private val people: PersonServiceGrpc.PersonServiceBlockingStub,
    private val credits: CreditServiceGrpc.CreditServiceBlockingStub,
    private val searches: SearchServiceGrpc.SearchServiceBlockingStub,
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

    /** A blank [nameContains] lists everyone. */
    fun listPeople(offset: Int, limit: Int, nameContains: String = ""): ListPeopleResponse =
        people.withDeadline().listPeople(
            ListPeopleRequest.newBuilder().setPage(page(offset, limit)).setNameContains(nameContains).build(),
        )

    fun getPerson(id: String): Person = people.withDeadline().getPerson(GetPersonRequest.newBuilder().setId(id).build())

    fun createPerson(fields: PersonFields): Person =
        people.withDeadline().createPerson(CreatePersonRequest.newBuilder().setFields(fields).build())

    fun updatePerson(id: String, expectedVersion: Int, fields: PersonFields): Person =
        people.withDeadline().updatePerson(
            UpdatePersonRequest.newBuilder().setId(id).setExpectedVersion(expectedVersion).setFields(fields).build(),
        )

    fun deletePerson(id: String, expectedVersion: Int): String =
        people.withDeadline().deletePerson(DeletePersonRequest.newBuilder().setId(id).setExpectedVersion(expectedVersion).build()).id

    /** Keyed by movie ID; IDs that do not exist are absent. */
    fun moviesByIds(ids: List<String>): Map<String, Movie> =
        movies.withDeadline().getMoviesByIds(GetMoviesByIdsRequest.newBuilder().addAllIds(ids).build()).moviesMap

    /** Keyed by person ID; IDs that do not exist are absent. */
    fun peopleByIds(ids: List<String>): Map<String, Person> =
        people.withDeadline().getPeopleByIds(GetPeopleByIdsRequest.newBuilder().addAllIds(ids).build()).peopleMap

    fun addCredit(movieId: String, personId: String, type: CreditType, characterName: String?, billingOrder: Int): Credit =
        credits.withDeadline().addCredit(
            AddCreditRequest.newBuilder()
                .setMovieId(movieId)
                .setPersonId(personId)
                .setType(type.toProto())
                .setBillingOrder(billingOrder)
                .apply { characterName?.let { setCharacterName(it) } }
                .build(),
        )

    fun updateCredit(id: String, type: CreditType, characterName: String?, billingOrder: Int): Credit =
        credits.withDeadline().updateCredit(
            UpdateCreditRequest.newBuilder()
                .setId(id)
                .setType(type.toProto())
                .setBillingOrder(billingOrder)
                .apply { characterName?.let { setCharacterName(it) } }
                .build(),
        )

    fun removeCredit(id: String): String = credits.withDeadline().removeCredit(RemoveCreditRequest.newBuilder().setId(id).build()).id

    /** Keyed by movie ID, in billing order; every requested ID has an entry, possibly empty. */
    fun creditsByMovieIds(movieIds: List<String>): Map<String, List<Credit>> =
        credits.withDeadline().getCreditsByMovieIds(GetCreditsByMovieIdsRequest.newBuilder().addAllMovieIds(movieIds).build())
            .creditsMap.mapValues { it.value.creditsList }

    /** Keyed by person ID, in billing order; every requested ID has an entry, possibly empty. */
    fun creditsByPersonIds(personIds: List<String>): Map<String, List<Credit>> =
        credits.withDeadline().getCreditsByPersonIds(GetCreditsByPersonIdsRequest.newBuilder().addAllPersonIds(personIds).build())
            .creditsMap.mapValues { it.value.creditsList }

    /** Movies and people matching [query], best match first. */
    fun search(query: String, limit: Int): List<SearchHit> =
        searches.withDeadline().search(SearchRequest.newBuilder().setQuery(query).setLimit(limit).build()).hitsList

    private fun page(offset: Int, limit: Int) = PageRequest.newBuilder().setOffset(offset).setLimit(limit)

    private fun <S : AbstractStub<S>> S.withDeadline(): S =
        withDeadlineAfter(properties.catalogDeadline.toMillis(), TimeUnit.MILLISECONDS)
}

private fun CreditType.toProto(): CreditTypeProto =
    when (this) {
        CreditType.CAST -> CreditTypeProto.CREDIT_TYPE_CAST
        CreditType.DIRECTOR -> CreditTypeProto.CREDIT_TYPE_DIRECTOR
        CreditType.WRITER -> CreditTypeProto.CREDIT_TYPE_WRITER
        CreditType.PRODUCER -> CreditTypeProto.CREDIT_TYPE_PRODUCER
    }
