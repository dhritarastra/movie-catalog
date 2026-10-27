package com.moviecatalog.gateway.graphql

import com.google.protobuf.Any
import com.google.protobuf.ByteString
import com.google.rpc.BadRequest
import com.google.rpc.Code
import com.moviecatalog.catalog.v1.ArtworkChangeResponse
import com.moviecatalog.catalog.v1.DeleteMovieResponse
import com.moviecatalog.catalog.v1.ListMoviesResponse
import com.moviecatalog.catalog.v1.Movie
import com.moviecatalog.catalog.v1.MovieFields
import com.moviecatalog.gateway.grpc.CatalogClient
import com.moviecatalog.gateway.grpc.MediaClient
import com.moviecatalog.gateway.grpc.UploadSource
import com.moviecatalog.gateway.movie.ArtworkUploader
import com.moviecatalog.media.v1.ArtworkVariant
import com.moviecatalog.media.v1.DownloadArtworkResponse
import com.moviecatalog.media.v1.DownloadMetadata
import com.ninjasquad.springmockk.MockkBean
import io.grpc.Status
import io.grpc.protobuf.StatusProto
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * The gateway's HTTP surface with the downstream services mocked: multipart routing and
 * parsing, the mapping from gRPC failures to GraphQL error codes, and image downloads.
 */
@SpringBootTest
class GatewayHttpTest(
    @Autowired context: WebApplicationContext,
    @Autowired graphQlService: ExecutionGraphQlService,
) {
    @MockkBean
    private lateinit var catalog: CatalogClient

    @MockkBean
    private lateinit var uploader: ArtworkUploader

    @MockkBean
    private lateinit var media: MediaClient

    private val mvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
    private val graphQl = ExecutionGraphQlServiceTester.create(graphQlService)
    private val poster = MockMultipartFile("0", "poster.png", "image/png", byteArrayOf(1, 2, 3, 4))

    @BeforeEach
    fun setUp() {
        every { uploader.upload(any(), any(), any()) } returns movie(version = 2, artworkId = ARTWORK)
    }

    @Test
    fun `a multipart request runs the mutation with the uploaded file`() {
        val source = slot<UploadSource>()
        every { uploader.upload(MOVIE, 1, capture(source)) } returns movie(version = 2, artworkId = ARTWORK)

        mvc.perform(
            multipart("/graphql")
                .file(poster)
                .param("operations", UPLOAD_OPERATION)
                .param("map", """{"0": ["variables.file"]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.uploadArtwork.version").value(2))
            .andExpect(jsonPath("$.data.uploadArtwork.artworkUrl").value("/media/$ARTWORK?variant=full"))

        verify { uploader.upload(MOVIE, 1, any()) }
        assertThat(source.captured.open().readAllBytes()).isEqualTo(byteArrayOf(1, 2, 3, 4))
        assertThat(source.captured.filename).isEqualTo("poster.png")
    }

    @Test
    fun `rejects a malformed multipart envelope with 400 and runs nothing`() {
        val cases = listOf(
            // map points at a variable the operation does not declare
            UPLOAD_OPERATION to """{"0": ["variables.nope"]}""",
            // map path outside variables
            UPLOAD_OPERATION to """{"0": ["query"]}""",
            // map names a file part that was not sent
            UPLOAD_OPERATION to """{"1": ["variables.file"]}""",
            // parts that are not JSON objects, including a literal null
            "not json" to """{"0": ["variables.file"]}""",
            UPLOAD_OPERATION to "[",
            "null" to """{"0": ["variables.file"]}""",
            UPLOAD_OPERATION to "null",
            // a batch, which is not supported
            "[$UPLOAD_OPERATION]" to """{"0": ["0.variables.file"]}""",
            // an envelope without a usable query, or with wrongly typed fields
            """{"variables": {"file": null}}""" to """{"0": ["variables.file"]}""",
            """{"query": 5, "variables": {"file": null}}""" to """{"0": ["variables.file"]}""",
            """{"query": "{ movies { totalCount } }", "variables": [null]}""" to """{"0": ["variables.0"]}""",
            """{"query": "{ movies { totalCount } }", "operationName": 7}""" to "{}",
        )
        cases.forEach { (operations, map) ->
            mvc.perform(multipart("/graphql").file(poster).param("operations", operations).param("map", map))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[0].extensions.code").value("BAD_REQUEST"))
        }
        // operations part missing entirely
        mvc.perform(multipart("/graphql").file(poster).param("map", "{}")).andExpect(status().isBadRequest)

        verify(exactly = 0) { uploader.upload(any(), any(), any()) }
    }

    @Test
    fun `an Upload cannot be written inline in the query`() {
        mvc.perform(
            multipart("/graphql")
                .file(poster)
                .param("operations", """{"query": "mutation { uploadArtwork(movieId: \"$MOVIE\", version: 1, file: \"x\") { version } }"}""")
                .param("map", "{}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].message").exists())
            .andExpect(jsonPath("$.data").doesNotExist())

        verify(exactly = 0) { uploader.upload(any(), any(), any()) }
    }

    @Test
    fun `validation failures become BAD_REQUEST with camelCase field errors`() {
        val badRequest = BadRequest.newBuilder()
            .addFieldViolations(BadRequest.FieldViolation.newBuilder().setField("title").setDescription("is required"))
            .addFieldViolations(BadRequest.FieldViolation.newBuilder().setField("runtime_minutes").setDescription("must be between 1 and 1000"))
            .build()
        every { catalog.createMovie(any()) } throws StatusProto.toStatusRuntimeException(
            com.google.rpc.Status.newBuilder().setCode(Code.INVALID_ARGUMENT_VALUE).setMessage("invalid").addDetails(Any.pack(badRequest)).build(),
        )

        graphQl.document("""mutation { createMovie(input: {title: "", runtimeMinutes: 0, genres: []}) { id } }""")
            .execute()
            .errors().satisfy { errors ->
                val extensions = errors.single().extensions
                assertThat(extensions["code"]).isEqualTo("BAD_REQUEST")
                assertThat(extensions["fieldErrors"]).isEqualTo(
                    listOf(
                        mapOf("field" to "title", "message" to "is required"),
                        mapOf("field" to "runtimeMinutes", "message" to "must be between 1 and 1000"),
                    ),
                )
            }
    }

    @Test
    fun `each gRPC status maps to its GraphQL error code`() {
        mapOf(
            Status.ABORTED to "CONFLICT",
            Status.FAILED_PRECONDITION to "FAILED_PRECONDITION",
            Status.UNAVAILABLE to "SERVICE_UNAVAILABLE",
            Status.DEADLINE_EXCEEDED to "SERVICE_UNAVAILABLE",
            Status.INTERNAL to "INTERNAL_ERROR",
        ).forEach { (status, code) ->
            every { catalog.listMovies(any(), any()) } throws status.withDescription("internal detail").asRuntimeException()

            graphQl.document("{ movies { totalCount } }")
                .execute()
                .errors().satisfy { errors ->
                    assertThat(errors.single().extensions["code"]).describedAs(status.code.name).isEqualTo(code)
                    if (code == "INTERNAL_ERROR") {
                        assertThat(errors.single().message).doesNotContain("internal detail")
                    }
                }
        }
    }

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
    fun `an image download sends cache headers only with a successful body`() {
        every { media.download(ARTWORK, ArtworkVariant.ARTWORK_VARIANT_THUMBNAIL) } returns
            iterator {
                yield(metadata())
                yield(DownloadArtworkResponse.newBuilder().setChunk(ByteString.copyFrom(byteArrayOf(7, 8, 9))).build())
            }

        mvc.perform(get("/media/$ARTWORK").param("variant", "thumb"))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
            .andExpect(header().string("ETag", "\"abc123-thumb\""))
            .andExpect(content().bytes(byteArrayOf(7, 8, 9)))
    }

    @Test
    fun `a download that fails after its metadata is answered with an uncached error`() {
        every { media.download(ARTWORK, any()) } returns
            iterator {
                yield(metadata())
                throw Status.INTERNAL.withDescription("The artwork could not be read").asRuntimeException()
            }

        mvc.perform(get("/media/$ARTWORK"))
            .andExpect(status().isBadGateway)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().doesNotExist("ETag"))
    }

    @Test
    fun `a missing or unavailable image is never cached`() {
        every { media.download("missing", any()) } returns iterator { throw Status.NOT_FOUND.asRuntimeException() }
        every { media.download("slow", any()) } returns iterator { throw Status.DEADLINE_EXCEEDED.asRuntimeException() }

        mvc.perform(get("/media/missing")).andExpect(status().isNotFound).andExpect(header().string("Cache-Control", "no-store"))
        mvc.perform(get("/media/slow")).andExpect(status().isServiceUnavailable).andExpect(header().string("Cache-Control", "no-store"))
        mvc.perform(get("/media/$ARTWORK").param("variant", "huge")).andExpect(status().isBadRequest)
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

    private fun metadata() =
        DownloadArtworkResponse.newBuilder()
            .setMetadata(DownloadMetadata.newBuilder().setContentType("image/png").setSizeBytes(3).setSha256("abc123"))
            .build()

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
        val UPLOAD_OPERATION = """
            {"query": "mutation(${'$'}file: Upload!) { uploadArtwork(movieId: \"$MOVIE\", version: 1, file: ${'$'}file) { version artworkUrl } }",
             "variables": {"file": null}}
        """.trimIndent()
    }
}
