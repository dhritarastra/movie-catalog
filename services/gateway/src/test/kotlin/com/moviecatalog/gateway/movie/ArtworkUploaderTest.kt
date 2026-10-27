package com.moviecatalog.gateway.movie

import com.moviecatalog.catalog.v1.ArtworkChangeResponse
import com.moviecatalog.catalog.v1.Movie
import com.moviecatalog.gateway.GatewayProperties
import com.moviecatalog.gateway.graphql.ErrorCode
import com.moviecatalog.gateway.graphql.GatewayException
import com.moviecatalog.gateway.grpc.CatalogClient
import com.moviecatalog.gateway.grpc.MediaClient
import com.moviecatalog.gateway.grpc.UploadSource
import com.moviecatalog.media.v1.Artwork
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/** The cross-service consistency rules for uploads (docs/PLAN.md §6), with both services mocked. */
class ArtworkUploaderTest {
    private val catalog = mockk<CatalogClient>()
    private val media = mockk<MediaClient>()
    private val uploader = ArtworkUploader(
        catalog,
        media,
        ArtworkCleaner(media),
        GatewayProperties(MAX_BYTES, Duration.ofSeconds(2), Duration.ofSeconds(30), Duration.ofSeconds(30)),
    )
    private val source = UploadSource("image/png", 100, "poster.png") { ByteArray(100).inputStream() }

    @BeforeEach
    fun setUp() {
        every { catalog.getMovie(MOVIE) } returns Movie.newBuilder().setId(MOVIE).setVersion(1).build()
        every { media.upload(any()) } returns Artwork.newBuilder().setId(NEW_ARTWORK).build()
        every { media.delete(any()) } returns true
    }

    @Test
    fun `attaches the upload and deletes the artwork it replaced`() {
        every { catalog.setArtwork(MOVIE, 1, NEW_ARTWORK) } returns attached(previous = OLD_ARTWORK)

        val movie = uploader.upload(MOVIE, 1, source)

        assertThat(movie.artworkId).isEqualTo(NEW_ARTWORK)
        verify(exactly = 1) { media.delete(OLD_ARTWORK) }
        verify(exactly = 0) { media.delete(NEW_ARTWORK) }
    }

    @Test
    fun `deletes nothing when the movie had no artwork`() {
        every { catalog.setArtwork(MOVIE, 1, NEW_ARTWORK) } returns attached(previous = null)

        uploader.upload(MOVIE, 1, source)

        verify(exactly = 0) { media.delete(any()) }
    }

    @Test
    fun `deletes the new upload when the catalog definitely rejects it`() {
        listOf(Status.ABORTED, Status.NOT_FOUND, Status.INVALID_ARGUMENT).forEach { status ->
            every { catalog.setArtwork(MOVIE, 1, NEW_ARTWORK) } throws status.asRuntimeException()

            val error = assertThrows<StatusRuntimeException> { uploader.upload(MOVIE, 1, source) }

            assertThat(error.status.code).isEqualTo(status.code)
        }
        verify(exactly = 3) { media.delete(NEW_ARTWORK) }
    }

    @Test
    fun `keeps the new upload when the outcome is unknown, because the attach may have committed`() {
        listOf(Status.DEADLINE_EXCEEDED, Status.UNAVAILABLE, Status.INTERNAL).forEach { status ->
            every { catalog.setArtwork(MOVIE, 1, NEW_ARTWORK) } throws status.asRuntimeException()

            assertThrows<StatusRuntimeException> { uploader.upload(MOVIE, 1, source) }
        }
        verify(exactly = 0) { media.delete(any()) }
    }

    @Test
    fun `a failed clean-up delete does not fail an upload that succeeded`() {
        every { catalog.setArtwork(MOVIE, 1, NEW_ARTWORK) } returns attached(previous = OLD_ARTWORK)
        every { media.delete(OLD_ARTWORK) } throws Status.UNAVAILABLE.asRuntimeException()

        assertThat(uploader.upload(MOVIE, 1, source).artworkId).isEqualTo(NEW_ARTWORK)
    }

    @Test
    fun `checks the movie exists before uploading anything`() {
        every { catalog.getMovie(MOVIE) } throws Status.NOT_FOUND.asRuntimeException()

        assertThrows<StatusRuntimeException> { uploader.upload(MOVIE, 1, source) }

        verify(exactly = 0) { media.upload(any()) }
    }

    @Test
    fun `does not attach when media rejects the file`() {
        every { media.upload(any()) } throws Status.INVALID_ARGUMENT.withDescription("Only JPEG and PNG").asRuntimeException()

        assertThrows<StatusRuntimeException> { uploader.upload(MOVIE, 1, source) }

        verify(exactly = 0) { catalog.setArtwork(any(), any(), any()) }
        verify(exactly = 0) { media.delete(any()) }
    }

    @Test
    fun `rejects empty and oversized files without calling any service`() {
        val empty = assertThrows<GatewayException> { uploader.upload(MOVIE, 1, source.copy(sizeBytes = 0)) }
        val tooBig = assertThrows<GatewayException> { uploader.upload(MOVIE, 1, source.copy(sizeBytes = MAX_BYTES + 1)) }

        assertThat(empty.code).isEqualTo(ErrorCode.BAD_REQUEST)
        assertThat(tooBig.code).isEqualTo(ErrorCode.RESOURCE_EXHAUSTED)
        verify(exactly = 0) { catalog.getMovie(any()) }
        verify(exactly = 0) { media.upload(any()) }
    }

    @Test
    fun `accepts a file exactly at the size limit`() {
        every { catalog.setArtwork(MOVIE, 1, NEW_ARTWORK) } returns attached(previous = null)

        uploader.upload(MOVIE, 1, source.copy(sizeBytes = MAX_BYTES))

        verify(exactly = 1) { media.upload(any()) }
    }

    private fun attached(previous: String?) =
        ArtworkChangeResponse.newBuilder()
            .setMovie(Movie.newBuilder().setId(MOVIE).setVersion(2).setArtworkId(NEW_ARTWORK))
            .apply { previous?.let { setPreviousArtworkId(it) } }
            .build()

    companion object {
        const val MAX_BYTES = 5_242_880L
        const val MOVIE = "70b3e304-c409-4e26-82c2-13889b1a9b4a"
        const val NEW_ARTWORK = "11111111-1111-1111-1111-111111111111"
        const val OLD_ARTWORK = "22222222-2222-2222-2222-222222222222"
    }
}
