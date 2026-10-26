package com.moviecatalog.gateway.graphql

import com.google.protobuf.Any
import com.google.rpc.BadRequest
import com.google.rpc.Code
import com.moviecatalog.catalog.v1.ListMoviesResponse
import com.moviecatalog.catalog.v1.Movie
import com.moviecatalog.gateway.grpc.CatalogClient
import com.moviecatalog.gateway.grpc.UploadSource
import com.moviecatalog.gateway.movie.ArtworkUploader
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * The gateway's GraphQL surface with the downstream services mocked: multipart routing and
 * parsing over HTTP, and the mapping from gRPC failures to GraphQL error codes.
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
            // parts that are not JSON objects
            "not json" to """{"0": ["variables.file"]}""",
            UPLOAD_OPERATION to "[",
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
