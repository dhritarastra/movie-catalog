package com.moviecatalog.media.artwork

import io.grpc.Status
import io.grpc.StatusException
import org.springframework.grpc.server.exception.GrpcExceptionHandler
import org.springframework.stereotype.Component

/** Translates [MediaException]s into gRPC statuses; anything else is left to Spring gRPC (UNKNOWN). */
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
