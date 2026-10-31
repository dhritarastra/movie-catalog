package com.moviecatalog.gateway.movie

import com.google.protobuf.ByteString
import com.moviecatalog.gateway.grpc.MediaClient
import com.moviecatalog.media.v1.ArtworkVariant
import com.moviecatalog.media.v1.DownloadArtworkResponse
import com.moviecatalog.media.v1.DownloadMetadata
import com.ninjasquad.springmockk.MockkBean
import io.grpc.Status
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
class MediaControllerTest(
    @Autowired context: WebApplicationContext,
) {
    @MockkBean
    private lateinit var media: MediaClient

    private val mvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()

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

    private fun metadata() =
        DownloadArtworkResponse.newBuilder()
            .setMetadata(DownloadMetadata.newBuilder().setContentType("image/png").setSizeBytes(3).setSha256("abc123"))
            .build()

    companion object {
        const val ARTWORK = "11111111-1111-1111-1111-111111111111"
    }
}
