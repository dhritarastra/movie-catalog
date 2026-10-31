package com.moviecatalog.gateway.movie

import com.moviecatalog.gateway.grpc.MediaClient
import io.grpc.StatusRuntimeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Deletes artwork the catalog no longer points to. Only called after the catalog has confirmed,
 * so a failure leaves an orphaned file, never a movie with a missing image. Failures are only logged.
 */
@Component
class ArtworkCleaner(private val media: MediaClient) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun deleteQuietly(artworkId: String, reason: String) {
        try {
            media.delete(artworkId)
        } catch (e: StatusRuntimeException) {
            log.warn("Could not delete artwork {} after {}: {}", artworkId, reason, e.status)
        }
    }
}
