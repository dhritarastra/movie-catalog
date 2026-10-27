package com.moviecatalog.media.artwork

/** Failures media reports to callers. [MediaGrpcExceptionHandler] maps each to a gRPC status. */
sealed class MediaException(message: String) : RuntimeException(message)
