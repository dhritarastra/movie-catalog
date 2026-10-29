package com.moviecatalog.catalog.search

import com.moviecatalog.catalog.CATALOG_IN_PROCESS_NAME
import com.moviecatalog.catalog.CatalogGrpcTest
import com.moviecatalog.catalog.badRequestFields
import com.moviecatalog.catalog.v1.CreateMovieRequest
import com.moviecatalog.catalog.v1.CreatePersonRequest
import com.moviecatalog.catalog.v1.MovieFields
import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.catalog.v1.PersonFields
import com.moviecatalog.catalog.v1.PersonServiceGrpc
import com.moviecatalog.catalog.v1.SearchHit
import com.moviecatalog.catalog.v1.SearchRequest
import com.moviecatalog.catalog.v1.SearchServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient

/** Drives the real search endpoint in-process against Postgres. */
@CatalogGrpcTest
class SearchGrpcServiceTest(@Autowired private val jdbc: JdbcClient) {
    private val channel: ManagedChannel = InProcessChannelBuilder.forName(CATALOG_IN_PROCESS_NAME).directExecutor().build()
    private val search = SearchServiceGrpc.newBlockingStub(channel)
    private val movies = MovieServiceGrpc.newBlockingStub(channel)
    private val people = PersonServiceGrpc.newBlockingStub(channel)

    @BeforeEach
    fun emptyTables() {
        jdbc.sql("DELETE FROM credit").update()
        jdbc.sql("DELETE FROM movie").update()
        jdbc.sql("DELETE FROM person").update()
    }

    @AfterEach
    fun closeChannel() {
        channel.shutdownNow()
    }

    @Test
    fun `returns movies and people as one ranked list, each with its full fields`() {
        movies.createMovie(CreateMovieRequest.newBuilder().setFields(MovieFields.newBuilder().setTitle("Arrival").setReleaseDate("2016-11-11")).build())
        people.createPerson(CreatePersonRequest.newBuilder().setFields(PersonFields.newBuilder().setName("Arrival Fan")).build())

        val hits = search.search(SearchRequest.newBuilder().setQuery(" ARRIVAL ").setLimit(10).build()).hitsList

        assertThat(hits.map { it.hitCase }).containsExactly(SearchHit.HitCase.MOVIE, SearchHit.HitCase.PERSON)
        assertThat(hits[0].movie.releaseDate).isEqualTo("2016-11-11")
        assertThat(hits[1].person.name).isEqualTo("Arrival Fan")
    }

    @Test
    fun `a blank query and an omitted limit are reported field by field`() {
        val error = assertThrows<StatusRuntimeException> { search.search(SearchRequest.newBuilder().setQuery("  ").build()) }

        assertThat(badRequestFields(error)).containsExactly("query", "limit")
    }

    @Test
    fun `a limit above the configured maximum is rejected, not cut down`() {
        val error = assertThrows<StatusRuntimeException> { search.search(SearchRequest.newBuilder().setQuery("a").setLimit(51).build()) }

        assertThat(badRequestFields(error)).containsExactly("limit")
    }
}
