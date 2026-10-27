package com.moviecatalog.gateway.movie

import com.moviecatalog.gateway.grpc.MediaClient
import io.grpc.Status
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class ArtworkCleanerTest {
    private val media = mockk<MediaClient>()
    private val cleaner = ArtworkCleaner(media)

    @Test
    fun `deletes the file through media`() {
        every { media.delete("artwork") } returns true

        cleaner.deleteQuietly("artwork", "it was replaced")

        verify { media.delete("artwork") }
    }

    @Test
    fun `a media failure is logged, never thrown, because the user's change already succeeded`() {
        every { media.delete(any()) } throws Status.UNAVAILABLE.asRuntimeException()

        assertDoesNotThrow { cleaner.deleteQuietly("artwork", "it was replaced") }
    }
}
