package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.CATALOG_IN_PROCESS_NAME
import com.moviecatalog.catalog.CatalogGrpcTest
import com.moviecatalog.catalog.badRequestFields
import com.moviecatalog.catalog.v1.ClearArtworkRequest
import com.moviecatalog.catalog.v1.CreateMovieRequest
import com.moviecatalog.catalog.v1.DeleteMovieRequest
import com.moviecatalog.catalog.v1.GetMovieRequest
import com.moviecatalog.catalog.v1.GetMoviesByIdsRequest
import com.moviecatalog.catalog.v1.ListMoviesRequest
import com.moviecatalog.catalog.v1.MovieFields
import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.catalog.v1.PageRequest
import com.moviecatalog.catalog.v1.SetArtworkRequest
import com.moviecatalog.catalog.v1.UpdateMovieRequest
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import com.moviecatalog.catalog.v1.Movie as MovieProto

/** Drives the real gRPC endpoint in-process against Postgres: mapping, rules and SQL together. */
@CatalogGrpcTest
class MovieGrpcServiceTest(@Autowired private val jdbc: JdbcClient) {
    private val channel: ManagedChannel = InProcessChannelBuilder.forName(CATALOG_IN_PROCESS_NAME).directExecutor().build()
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

    @Test
    fun `an update replaces every editable field, keeps the artwork and bumps the version`() {
        val movie = create(
            MovieFields.newBuilder().setTitle("Arrival").setSynopsis("Heptapods.").setRuntimeMinutes(116).addAllGenres(listOf("Sci-Fi")),
        )
        val artwork = UUID.randomUUID().toString()
        movies.setArtwork(setArtwork(movie.id, version = 1, artworkId = artwork))

        // Only the title is sent: synopsis and runtime are cleared, and the empty list clears the genres.
        val updated = movies.updateMovie(update(movie.id, version = 2, MovieFields.newBuilder().setTitle("  Arrival (2016) ")))

        assertThat(updated.title).isEqualTo("Arrival (2016)")
        assertThat(updated.hasSynopsis()).isFalse()
        assertThat(updated.hasRuntimeMinutes()).isFalse()
        assertThat(updated.genresList).isEmpty()
        assertThat(updated.artworkId).isEqualTo(artwork)
        assertThat(updated.version).isEqualTo(3)
    }

    @Test
    fun `an update from a stale version is a conflict and changes nothing`() {
        val movie = create(MovieFields.newBuilder().setTitle("Arrival"))
        movies.updateMovie(update(movie.id, version = 1, MovieFields.newBuilder().setTitle("First edit")))

        val error = assertThrows<StatusRuntimeException> {
            movies.updateMovie(update(movie.id, version = 1, MovieFields.newBuilder().setTitle("Second edit")))
        }

        assertThat(error.status.code).isEqualTo(Status.Code.ABORTED)
        assertThat(error.status.description).contains("version 2, not 1")
        assertThat(get(movie.id).title).isEqualTo("First edit")
    }

    @Test
    fun `an update reports invalid fields, an omitted version and an unknown movie`() {
        val movie = create(MovieFields.newBuilder().setTitle("Arrival"))

        val invalid = assertThrows<StatusRuntimeException> {
            movies.updateMovie(update(movie.id, version = 1, MovieFields.newBuilder().setTitle(" ").setRuntimeMinutes(0)))
        }
        assertThat(badRequestFields(invalid)).containsExactly("title", "runtime_minutes")

        val omitted = assertThrows<StatusRuntimeException> {
            movies.updateMovie(update(movie.id, version = 0, MovieFields.newBuilder().setTitle("Edit")))
        }
        assertThat(badRequestFields(omitted)).containsExactly("expected_version")

        val unknown = assertThrows<StatusRuntimeException> {
            movies.updateMovie(update(UUID.randomUUID().toString(), version = 1, MovieFields.newBuilder().setTitle("Edit")))
        }
        assertThat(unknown.status.code).isEqualTo(Status.Code.NOT_FOUND)
        assertThat(get(movie.id).version).isEqualTo(1)
    }

    @Test
    fun `deleting returns the artwork it had, so the gateway can delete the file`() {
        val withArtwork = create(MovieFields.newBuilder().setTitle("Arrival"))
        val artwork = UUID.randomUUID().toString()
        movies.setArtwork(setArtwork(withArtwork.id, version = 1, artworkId = artwork))
        val withoutArtwork = create(MovieFields.newBuilder().setTitle("Dune"))

        val first = movies.deleteMovie(delete(withArtwork.id, version = 2))
        val second = movies.deleteMovie(delete(withoutArtwork.id, version = 1))

        assertThat(first.id).isEqualTo(withArtwork.id)
        assertThat(first.artworkId).isEqualTo(artwork)
        assertThat(second.hasArtworkId()).isFalse()
        val gone = assertThrows<StatusRuntimeException> { get(withArtwork.id) }
        assertThat(gone.status.code).isEqualTo(Status.Code.NOT_FOUND)
    }

    @Test
    fun `deleting from a stale version keeps the movie, and deleting twice reports it gone`() {
        val movie = create(MovieFields.newBuilder().setTitle("Arrival"))
        movies.updateMovie(update(movie.id, version = 1, MovieFields.newBuilder().setTitle("Edited")))

        val stale = assertThrows<StatusRuntimeException> { movies.deleteMovie(delete(movie.id, version = 1)) }
        assertThat(stale.status.code).isEqualTo(Status.Code.ABORTED)
        assertThat(get(movie.id).title).isEqualTo("Edited")

        movies.deleteMovie(delete(movie.id, version = 2))
        val again = assertThrows<StatusRuntimeException> { movies.deleteMovie(delete(movie.id, version = 2)) }
        assertThat(again.status.code).isEqualTo(Status.Code.NOT_FOUND)
    }

    @Test
    fun `clearing artwork returns what was removed and bumps the version`() {
        val movie = create(MovieFields.newBuilder().setTitle("Arrival"))
        val artwork = UUID.randomUUID().toString()
        movies.setArtwork(setArtwork(movie.id, version = 1, artworkId = artwork))

        val cleared = movies.clearArtwork(clear(movie.id, version = 2))

        assertThat(cleared.previousArtworkId).isEqualTo(artwork)
        assertThat(cleared.movie.hasArtworkId()).isFalse()
        assertThat(cleared.movie.version).isEqualTo(3)

        val stale = assertThrows<StatusRuntimeException> { movies.clearArtwork(clear(movie.id, version = 2)) }
        assertThat(stale.status.code).isEqualTo(Status.Code.ABORTED)
    }

    @Test
    fun `saving identical fields still advances the version and rejects the old version`() {
        val fields = MovieFields.newBuilder().setTitle("Arrival")
        val movie = create(fields)
        val saved = movies.updateMovie(update(movie.id, movie.version, fields))

        assertThat(saved.version).isEqualTo(2)
        assertThat(get(movie.id).version).isEqualTo(2)
        val stale = assertThrows<StatusRuntimeException> { movies.updateMovie(update(movie.id, movie.version, fields)) }
        assertThat(stale.status.code).isEqualTo(Status.Code.ABORTED)
    }

    @Test
    fun `unchanged artwork mutations still advance the version exactly once`() {
        val movie = create(MovieFields.newBuilder().setTitle("Arrival"))
        val cleared = movies.clearArtwork(clear(movie.id, version = 1))
        assertThat(cleared.movie.version).isEqualTo(2)
        assertThat(cleared.hasPreviousArtworkId()).isFalse()

        val artwork = UUID.randomUUID().toString()
        movies.setArtwork(setArtwork(movie.id, version = 2, artworkId = artwork))
        val same = movies.setArtwork(setArtwork(movie.id, version = 3, artworkId = artwork))
        assertThat(same.movie.version).isEqualTo(4)
        assertThat(same.previousArtworkId).isEqualTo(artwork)
        assertThat(get(movie.id).version).isEqualTo(4)
    }

    @Test
    fun `looks up several movies at once, leaving out ids that do not exist`() {
        val arrival = create(MovieFields.newBuilder().setTitle("Arrival"))
        val dune = create(MovieFields.newBuilder().setTitle("Dune"))
        val missing = UUID.randomUUID().toString()

        val found = movies.getMoviesByIds(GetMoviesByIdsRequest.newBuilder().addAllIds(listOf(arrival.id, dune.id, missing)).build())

        assertThat(found.moviesMap.keys).containsExactlyInAnyOrder(arrival.id, dune.id)
        assertThat(found.moviesMap[dune.id]!!.title).isEqualTo("Dune")
        val malformed = assertThrows<StatusRuntimeException> { movies.getMoviesByIds(GetMoviesByIdsRequest.newBuilder().addIds("42").build()) }
        assertThat(badRequestFields(malformed)).containsExactly("ids")
    }

    private fun get(id: String): MovieProto = movies.getMovie(GetMovieRequest.newBuilder().setId(id).build())

    private fun update(id: String, version: Int, fields: MovieFields.Builder) =
        UpdateMovieRequest.newBuilder().setId(id).setExpectedVersion(version).setFields(fields).build()

    private fun delete(id: String, version: Int) = DeleteMovieRequest.newBuilder().setId(id).setExpectedVersion(version).build()

    private fun clear(id: String, version: Int) = ClearArtworkRequest.newBuilder().setMovieId(id).setExpectedVersion(version).build()

    private fun create(fields: MovieFields.Builder): MovieProto =
        movies.createMovie(CreateMovieRequest.newBuilder().setFields(fields).build())

    private fun list(offset: Int, limit: Int) =
        movies.listMovies(
            ListMoviesRequest.newBuilder().setPage(PageRequest.newBuilder().setOffset(offset).setLimit(limit)).build(),
        )

    private fun setArtwork(movieId: String, version: Int, artworkId: String) =
        SetArtworkRequest.newBuilder().setMovieId(movieId).setExpectedVersion(version).setArtworkId(artworkId).build()
}
