package com.moviecatalog.gateway.graphql

import com.moviecatalog.catalog.v1.Movie
import com.moviecatalog.gateway.grpc.UploadSource
import com.moviecatalog.gateway.movie.ArtworkUploader
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/** Multipart routing and parsing over HTTP, with the artwork upload mocked out. */
@SpringBootTest
class GraphQlMultipartHandlerTest(
    @Autowired context: WebApplicationContext,
) {
    @MockkBean
    private lateinit var uploader: ArtworkUploader

    private val mvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
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
