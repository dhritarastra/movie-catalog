package com.moviecatalog.gateway.movie

import com.moviecatalog.catalog.v1.ArtworkChangeResponse
import com.moviecatalog.catalog.v1.DeleteMovieResponse
import com.moviecatalog.catalog.v1.ListMoviesResponse
import com.moviecatalog.catalog.v1.Movie
import com.moviecatalog.catalog.v1.MovieFields
import com.moviecatalog.gateway.grpc.CatalogClient
import com.moviecatalog.gateway.grpc.MediaClient
import com.ninjasquad.springmockk.MockkBean
import io.grpc.Status
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester

@SpringBootTest
class MovieControllerTest(
    @Autowired graphQlService: ExecutionGraphQlService,
) {
    @MockkBean
    private lateinit var catalog: CatalogClient

    @MockkBean
    private lateinit var media: MediaClient

    private val graphQl = ExecutionGraphQlServiceTester.create(graphQlService)

    @Test
    fun `a missing movie is null rather than an error`() {
        every { catalog.getMovie(MOVIE) } throws Status.NOT_FOUND.asRuntimeException()

        graphQl.document("""{ movie(id: "$MOVIE") { id } }""").execute().path("movie").valueIsNull()
    }

    @Test
    fun `artworkUrl defaults to the full image, honours THUMBNAIL and rejects an explicit null as a client error`() {
        every { catalog.getMovie(MOVIE) } returns movie(version = 2, artworkId = ARTWORK)

        graphQl.document("""{ movie(id: "$MOVIE") { artworkUrl } }""")
            .execute().path("movie.artworkUrl").entity(String::class.java).isEqualTo("/media/$ARTWORK?variant=full")
        graphQl.document("""{ movie(id: "$MOVIE") { artworkUrl(variant: THUMBNAIL) } }""")
            .execute().path("movie.artworkUrl").entity(String::class.java).isEqualTo("/media/$ARTWORK?variant=thumb")
        graphQl.document("""{ movie(id: "$MOVIE") { artworkUrl(variant: null) } }""")
            .execute()
            .errors().satisfy { errors ->
                assertThat(errors.single().errorType.toString()).isEqualTo("ValidationError")
            }
    }

    @Test
    fun `updateMovie sends the full field set, so omitted optional fields are cleared`() {
        val fields = slot<MovieFields>()
        every { catalog.updateMovie(MOVIE, 3, capture(fields)) } returns movie(version = 4, artworkId = ARTWORK)

        graphQl.document("""mutation { updateMovie(id: "$MOVIE", version: 3, input: {title: "Arrival", genres: []}) { version } }""")
            .execute().path("updateMovie.version").entity(Int::class.java).isEqualTo(4)

        assertThat(fields.captured.title).isEqualTo("Arrival")
        assertThat(fields.captured.hasSynopsis()).isFalse()
        assertThat(fields.captured.hasRuntimeMinutes()).isFalse()
        assertThat(fields.captured.genresList).isEmpty()
    }

    @Test
    fun `deleteMovie deletes the artwork file only after the catalog confirms`() {
        every { catalog.deleteMovie(MOVIE, 2) } returns DeleteMovieResponse.newBuilder().setId(MOVIE).setArtworkId(ARTWORK).build()
        every { media.delete(ARTWORK) } returns true

        graphQl.document("""mutation { deleteMovie(id: "$MOVIE", version: 2) }""")
            .execute().path("deleteMovie").entity(String::class.java).isEqualTo(MOVIE)

        verify(exactly = 1) { media.delete(ARTWORK) }
    }

    @Test
    fun `deleteMovie leaves media alone when there is no artwork or the catalog refuses`() {
        every { catalog.deleteMovie(MOVIE, 1) } returns DeleteMovieResponse.newBuilder().setId(MOVIE).build()
        every { catalog.deleteMovie(MOVIE, 5) } throws Status.ABORTED.withDescription("stale").asRuntimeException()

        graphQl.document("""mutation { deleteMovie(id: "$MOVIE", version: 1) }""").execute().path("deleteMovie").hasValue()
        graphQl.document("""mutation { deleteMovie(id: "$MOVIE", version: 5) }""")
            .execute()
            .errors().satisfy { errors -> assertThat(errors.single().extensions["code"]).isEqualTo("CONFLICT") }

        verify(exactly = 0) { media.delete(any()) }
    }

    @Test
    fun `a failed file clean-up does not fail a delete that succeeded`() {
        every { catalog.deleteMovie(MOVIE, 2) } returns DeleteMovieResponse.newBuilder().setId(MOVIE).setArtworkId(ARTWORK).build()
        every { media.delete(ARTWORK) } throws Status.UNAVAILABLE.asRuntimeException()

        graphQl.document("""mutation { deleteMovie(id: "$MOVIE", version: 2) }""")
            .execute().path("deleteMovie").entity(String::class.java).isEqualTo(MOVIE)
    }

    @Test
    fun `removeArtwork detaches the artwork, then deletes the file it pointed to`() {
        every { catalog.clearArtwork(MOVIE, 2) } returns
            ArtworkChangeResponse.newBuilder()
                .setMovie(Movie.newBuilder().setId(MOVIE).setTitle("Arrival").setVersion(3))
                .setPreviousArtworkId(ARTWORK)
                .build()
        every { media.delete(ARTWORK) } returns true

        graphQl.document("""mutation { removeArtwork(movieId: "$MOVIE", version: 2) { version artworkUrl } }""")
            .execute()
            .path("removeArtwork.version").entity(Int::class.java).isEqualTo(3)
            .path("removeArtwork.artworkUrl").valueIsNull()

        verify(exactly = 1) { media.delete(ARTWORK) }
    }

    @Test
    fun `an omitted or null page limit means the default of 20`() {
        every { catalog.listMovies(0, 20) } returns ListMoviesResponse.newBuilder().setTotalCount(0).build()

        graphQl.document("{ movies { limit } }").execute().path("movies.limit").entity(Int::class.java).isEqualTo(20)
        graphQl.document("{ movies(page: {limit: null}) { limit } }").execute().path("movies.limit").entity(Int::class.java).isEqualTo(20)

        verify(exactly = 2) { catalog.listMovies(0, 20) }
    }

    private fun movie(version: Int, artworkId: String) =
        Movie.newBuilder().setId(MOVIE).setTitle("Arrival").setVersion(version).setArtworkId(artworkId).build()

    companion object {
        const val MOVIE = "70b3e304-c409-4e26-82c2-13889b1a9b4a"
        const val ARTWORK = "11111111-1111-1111-1111-111111111111"
    }
}
