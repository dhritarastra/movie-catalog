package com.moviecatalog.gateway.movie

import com.moviecatalog.gateway.grpc.MediaClient
import io.grpc.StatusRuntimeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Deletes artwork files that the catalog no longer references: replaced, removed, belonging to a
 * deleted movie, or never attached. Callers only do this after the catalog has confirmed its side
 * (PLAN.md §6), so a failure here can leave an orphaned file but never a movie pointing at a
 * missing one. Failures are logged rather than failing the user's request, which already succeeded.
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
