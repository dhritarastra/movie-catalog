package com.moviecatalog.media.artwork

import io.grpc.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MediaGrpcExceptionHandlerTest {
    private val handler = MediaGrpcExceptionHandler()

    @Test
    fun `each media failure has its own status, carrying the message`() {
        val cases = mapOf(
            InvalidArtworkException("not an image") to Status.Code.INVALID_ARGUMENT,
            ArtworkTooLargeException(10) to Status.Code.RESOURCE_EXHAUSTED,
            ArtworkNotFoundException("gone") to Status.Code.NOT_FOUND,
        )

        cases.forEach { (exception, code) ->
            val status = handler.handleException(exception)!!.status
            assertThat(status.code).describedAs(exception.javaClass.simpleName).isEqualTo(code)
            assertThat(status.description).isEqualTo(exception.message)
        }
    }

    @Test
    fun `anything else is left to the default handling`() {
        assertThat(handler.handleException(IllegalStateException("bug"))).isNull()
    }
}
