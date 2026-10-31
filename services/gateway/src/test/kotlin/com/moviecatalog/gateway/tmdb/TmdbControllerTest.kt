package com.moviecatalog.gateway.tmdb

import com.moviecatalog.catalog.v1.Movie
import com.moviecatalog.gateway.graphql.ErrorCode
import com.moviecatalog.gateway.graphql.GatewayException
import com.moviecatalog.gateway.grpc.CatalogClient
import com.moviecatalog.gateway.grpc.UploadSource
import com.moviecatalog.gateway.movie.ArtworkUploader
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
class TmdbControllerTest(@Autowired graphQlService: ExecutionGraphQlService) {
    @MockkBean
    private lateinit var tmdb: TmdbClient

    @MockkBean
    private lateinit var catalog: CatalogClient

    @MockkBean
    private lateinit var uploader: ArtworkUploader

    private val graphQl = ExecutionGraphQlServiceTester.create(graphQlService)
    private val poster = TmdbPoster(329865, "Arrival", "2016", "/arrival.jpg")

    @Test
    fun `says whether poster lookup is available`() {
        every { tmdb.enabled } returns false

        graphQl.document("{ tmdbPostersEnabled }").execute().path("tmdbPostersEnabled").entity(Boolean::class.java).isEqualTo(false)
    }

    @Test
    fun `searches by the movie's title and release year`() {
        every { catalog.getMovie(MOVIE) } returns Movie.newBuilder().setId(MOVIE).setTitle("Arrival").setReleaseDate("2016-11-11").build()
        every { tmdb.findPoster("Arrival", "2016") } returns poster
        every { tmdb.previewUrl(poster) } returns "https://images.test/w342/arrival.jpg"

        graphQl.document("""{ tmdbPoster(movieId: "$MOVIE") { tmdbId title releaseYear previewUrl } }""")
            .execute()
            .path("tmdbPoster.tmdbId").entity(Int::class.java).isEqualTo(329865)
            .path("tmdbPoster.previewUrl").entity(String::class.java).isEqualTo("https://images.test/w342/arrival.jpg")
    }

    @Test
    fun `finds nothing for a movie TMDB does not know, searching without a year when there is none`() {
        every { catalog.getMovie(MOVIE) } returns Movie.newBuilder().setId(MOVIE).setTitle("Home Video").build()
        every { tmdb.findPoster("Home Video", null) } returns null

        graphQl.document("""{ tmdbPoster(movieId: "$MOVIE") { tmdbId } }""").execute().path("tmdbPoster").valueIsNull()
    }

    @Test
    fun `attaches the downloaded poster through the normal upload, with the version the user saw`() {
        val source = slot<UploadSource>()
        every { tmdb.poster(329865) } returns poster
        every { tmdb.download(poster) } returns byteArrayOf(1, 2, 3)
        every { uploader.upload(MOVIE, 4, capture(source)) } returns
            Movie.newBuilder().setId(MOVIE).setTitle("Arrival").setVersion(5).setArtworkId(ARTWORK).build()

        graphQl.document("""mutation { useTmdbPoster(movieId: "$MOVIE", version: 4, tmdbId: 329865) { version artworkUrl } }""")
            .execute()
            .path("useTmdbPoster.version").entity(Int::class.java).isEqualTo(5)
            .path("useTmdbPoster.artworkUrl").entity(String::class.java).isEqualTo("/media/$ARTWORK?variant=full")

        assertThat(source.captured.contentType).isEqualTo("image/jpeg")
        assertThat(source.captured.open().readAllBytes()).containsExactly(1, 2, 3)
    }

    @Test
    fun `reports an unknown film, TMDB failures and stale versions without attaching anything`() {
        every { tmdb.poster(1) } returns null
        every { tmdb.poster(2) } throws GatewayException(ErrorCode.TMDB_UNAVAILABLE, "TMDB could not be reached.")
        every { tmdb.poster(3) } returns poster
        every { tmdb.download(poster) } returns byteArrayOf(1)
        every { uploader.upload(MOVIE, 1, any()) } throws Status.ABORTED.withDescription("stale").asRuntimeException()

        mapOf(1 to "NOT_FOUND", 2 to "TMDB_UNAVAILABLE", 3 to "CONFLICT").forEach { (tmdbId, code) ->
            graphQl.document("""mutation { useTmdbPoster(movieId: "$MOVIE", version: 1, tmdbId: $tmdbId) { version } }""")
                .execute()
                .errors().satisfy { errors -> assertThat(errors.single().extensions["code"]).describedAs("$tmdbId").isEqualTo(code) }
        }
        verify(exactly = 1) { uploader.upload(any(), any(), any()) }
    }

    companion object {
        const val MOVIE = "70b3e304-c409-4e26-82c2-13889b1a9b4a"
        const val ARTWORK = "11111111-1111-1111-1111-111111111111"
    }
}
