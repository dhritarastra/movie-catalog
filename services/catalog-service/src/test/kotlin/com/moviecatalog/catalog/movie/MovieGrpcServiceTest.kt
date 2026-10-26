package com.moviecatalog.catalog.movie

import com.google.rpc.BadRequest
import com.moviecatalog.catalog.TestcontainersConfiguration
import com.moviecatalog.catalog.v1.CreateMovieRequest
import com.moviecatalog.catalog.v1.GetMovieRequest
import com.moviecatalog.catalog.v1.ListMoviesRequest
import com.moviecatalog.catalog.v1.MovieFields
import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.catalog.v1.PageRequest
import com.moviecatalog.catalog.v1.SetArtworkRequest
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.protobuf.StatusProto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import com.moviecatalog.catalog.v1.Movie as MovieProto

/** Drives the real gRPC endpoint in-process against Postgres: mapping, rules and SQL together. */
@SpringBootTest(properties = ["spring.grpc.server.port=0", "spring.grpc.server.inprocess.name=$IN_PROCESS_NAME"])
@Import(TestcontainersConfiguration::class)
class MovieGrpcServiceTest(@Autowired private val jdbc: JdbcClient) {
    private val channel: ManagedChannel = InProcessChannelBuilder.forName(IN_PROCESS_NAME).directExecutor().build()
    private val movies = MovieServiceGrpc.newBlockingStub(channel)

    @BeforeEach
    fun emptyTable() {
        jdbc.sql("DELETE FROM movie").update()
    }

    @AfterEach
    fun closeChannel() {
        channel.shutdownNow()
    }

    @Test
    fun `creates a movie with normalised fields at version 1`() {
        val created = create(
            MovieFields.newBuilder()
                .setTitle("  Arrival ")
                .setSynopsis("  ")
                .setReleaseDate("2016-11-11")
                .setRuntimeMinutes(116)
                .addAllGenres(listOf("Sci-Fi", "sci-fi", "Drama")),
        )

        assertThat(created.title).isEqualTo("Arrival")
        assertThat(created.hasSynopsis()).isFalse()
        assertThat(created.releaseDate).isEqualTo("2016-11-11")
        assertThat(created.runtimeMinutes).isEqualTo(116)
        assertThat(created.genresList).containsExactly("Sci-Fi", "Drama")
        assertThat(created.hasArtworkId()).isFalse()
        assertThat(created.version).isEqualTo(1)
        assertThat(movies.getMovie(GetMovieRequest.newBuilder().setId(created.id).build())).isEqualTo(created)
    }

    @Test
    fun `rejects invalid fields with a BadRequest detail per field`() {
        val error = assertThrows<StatusRuntimeException> {
            create(MovieFields.newBuilder().setTitle("").setRuntimeMinutes(0))
        }

        assertThat(error.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
        assertThat(badRequestFields(error)).containsExactly("title", "runtime_minutes")
    }

    @Test
    fun `reports unknown and malformed ids differently`() {
        val unknown = assertThrows<StatusRuntimeException> {
            movies.getMovie(GetMovieRequest.newBuilder().setId(UUID.randomUUID().toString()).build())
        }
        assertThat(unknown.status.code).isEqualTo(Status.Code.NOT_FOUND)

        val malformed = assertThrows<StatusRuntimeException> {
            movies.getMovie(GetMovieRequest.newBuilder().setId("42").build())
        }
        assertThat(malformed.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
        assertThat(badRequestFields(malformed)).containsExactly("id")
    }

    @Test
    fun `lists newest first in stable pages with the total count`() {
        val (first, second, third) = listOf("First", "Second", "Third").map { create(MovieFields.newBuilder().setTitle(it)) }

        val page1 = list(offset = 0, limit = 2)
        val page2 = list(offset = 2, limit = 2)
        val beyond = list(offset = 10, limit = 2)

        assertThat(page1.moviesList.map { it.id }).containsExactly(third.id, second.id)
        assertThat(page2.moviesList.map { it.id }).containsExactly(first.id)
        assertThat(beyond.moviesList).isEmpty()
        assertThat(listOf(page1, page2, beyond).map { it.totalCount }).containsOnly(3)
    }

    @Test
    fun `rejects out-of-range paging, including an omitted limit`() {
        listOf(0 to 0, 0 to 101, -1 to 10).forEach { (offset, limit) ->
            val error = assertThrows<StatusRuntimeException> { list(offset, limit) }
            assertThat(error.status.code).describedAs("offset=$offset limit=$limit").isEqualTo(Status.Code.INVALID_ARGUMENT)
        }
        val omitted = assertThrows<StatusRuntimeException> { movies.listMovies(ListMoviesRequest.getDefaultInstance()) }
        assertThat(omitted.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
    }

    @Test
    fun `attaching artwork bumps the version and returns the artwork it replaced`() {
        val movie = create(MovieFields.newBuilder().setTitle("Arrival"))
        val firstArtwork = UUID.randomUUID().toString()
        val secondArtwork = UUID.randomUUID().toString()

        val first = movies.setArtwork(setArtwork(movie.id, version = 1, artworkId = firstArtwork))
        val second = movies.setArtwork(setArtwork(movie.id, version = 2, artworkId = secondArtwork))

        assertThat(first.movie.artworkId).isEqualTo(firstArtwork)
        assertThat(first.movie.version).isEqualTo(2)
        assertThat(first.hasPreviousArtworkId()).isFalse()
        assertThat(second.movie.artworkId).isEqualTo(secondArtwork)
        assertThat(second.movie.version).isEqualTo(3)
        assertThat(second.previousArtworkId).isEqualTo(firstArtwork)
    }

    @Test
    fun `a stale version is a conflict and changes nothing`() {
        val movie = create(MovieFields.newBuilder().setTitle("Arrival"))
        movies.setArtwork(setArtwork(movie.id, version = 1, artworkId = UUID.randomUUID().toString()))

        val error = assertThrows<StatusRuntimeException> {
            movies.setArtwork(setArtwork(movie.id, version = 1, artworkId = UUID.randomUUID().toString()))
        }

        assertThat(error.status.code).isEqualTo(Status.Code.ABORTED)
        assertThat(movies.getMovie(GetMovieRequest.newBuilder().setId(movie.id).build()).version).isEqualTo(2)
    }

    @Test
    fun `attaching artwork rejects an omitted version and reports an unknown movie`() {
        val movie = create(MovieFields.newBuilder().setTitle("Arrival"))

        val omitted = assertThrows<StatusRuntimeException> {
            movies.setArtwork(setArtwork(movie.id, version = 0, artworkId = UUID.randomUUID().toString()))
        }
        assertThat(omitted.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
        assertThat(badRequestFields(omitted)).containsExactly("expected_version")

        val unknown = assertThrows<StatusRuntimeException> {
            movies.setArtwork(setArtwork(UUID.randomUUID().toString(), version = 1, artworkId = UUID.randomUUID().toString()))
        }
        assertThat(unknown.status.code).isEqualTo(Status.Code.NOT_FOUND)
    }

    private fun create(fields: MovieFields.Builder): MovieProto =
        movies.createMovie(CreateMovieRequest.newBuilder().setFields(fields).build())

    private fun list(offset: Int, limit: Int) =
        movies.listMovies(
            ListMoviesRequest.newBuilder().setPage(PageRequest.newBuilder().setOffset(offset).setLimit(limit)).build(),
        )

    private fun setArtwork(movieId: String, version: Int, artworkId: String) =
        SetArtworkRequest.newBuilder().setMovieId(movieId).setExpectedVersion(version).setArtworkId(artworkId).build()

    private fun badRequestFields(error: StatusRuntimeException): List<String> =
        StatusProto.fromThrowable(error)!!.detailsList
            .single { it.`is`(BadRequest::class.java) }
            .unpack(BadRequest::class.java)
            .fieldViolationsList.map { it.field }
}

private const val IN_PROCESS_NAME = "movie-grpc-test"
