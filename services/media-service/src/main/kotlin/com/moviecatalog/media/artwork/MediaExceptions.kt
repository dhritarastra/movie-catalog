package com.moviecatalog.media.artwork

import io.grpc.Status
import io.grpc.StatusException
import org.springframework.grpc.server.exception.GrpcExceptionHandler
import org.springframework.stereotype.Component

/** Failures media reports to callers. [MediaGrpcExceptionHandler] maps each to a gRPC status. */
sealed class MediaException(message: String) : RuntimeException(message)

/** The request or the file itself is unacceptable: wrong type, corrupt, too many pixels, bad ordering. */
class InvalidArtworkException(message: String) : MediaException(message)

class ArtworkTooLargeException(maxBytes: Long) : MediaException("The image is larger than the $maxBytes-byte limit")

class ArtworkNotFoundException(message: String) : MediaException(message)

@Component
class MediaGrpcExceptionHandler : GrpcExceptionHandler {
    override fun handleException(exception: Throwable): StatusException? =
        when (exception) {
            is InvalidArtworkException -> Status.INVALID_ARGUMENT.withDescription(exception.message).asException()
            is ArtworkTooLargeException -> Status.RESOURCE_EXHAUSTED.withDescription(exception.message).asException()
            is ArtworkNotFoundException -> Status.NOT_FOUND.withDescription(exception.message).asException()
            else -> null
        }
}
