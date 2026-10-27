package com.moviecatalog.gateway.movie

import com.moviecatalog.catalog.v1.Movie
import com.moviecatalog.gateway.config.GatewayProperties
import com.moviecatalog.gateway.graphql.ErrorCode
import com.moviecatalog.gateway.graphql.GatewayException
import com.moviecatalog.gateway.grpc.CatalogClient
import com.moviecatalog.gateway.grpc.MediaClient
import com.moviecatalog.gateway.grpc.UploadSource
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Uploads artwork and attaches it to a movie. Media and catalog are separate services, so there is no shared transaction:
 *
 * - The movie is checked first so a missing movie never costs an upload.
 * - After uploading, the catalog attaches the new artwork if the movie is still at the
 *   version the user saw.
 * - If the catalog definitely refused (missing movie, stale version, bad input), the new file
 *   is unattached and is deleted.
 * - If the outcome is unknown (timeout, lost connection), the catalog may have committed, so
 *   the new file is kept and the problem logged: an orphaned file is better than a movie
 *   pointing at a deleted one.
 * - The artwork that was replaced is deleted using the ID the catalog returned from the same
 *   transaction, never one read earlier.
 */
@Component
class ArtworkUploader(
    private val catalog: CatalogClient,
    private val media: MediaClient,
    private val cleaner: ArtworkCleaner,
    private val properties: GatewayProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upload(movieId: String, expectedVersion: Int, source: UploadSource): Movie {
        if (source.sizeBytes == 0L) throw GatewayException(ErrorCode.BAD_REQUEST, "The file is empty")
        if (source.sizeBytes > properties.maxUploadBytes) {
            throw GatewayException(ErrorCode.RESOURCE_EXHAUSTED, "The image must be at most ${properties.maxUploadBytes / (1024 * 1024)} MB")
        }
        catalog.getMovie(movieId)

        val artwork = media.upload(source)

        val change = try {
            catalog.setArtwork(movieId, expectedVersion, artwork.id)
        } catch (e: StatusRuntimeException) {
            if (e.status.code in DEFINITE_REJECTIONS) {
                cleaner.deleteQuietly(artwork.id, "the catalog rejected attaching it to movie $movieId (${e.status.code})")
            } else {
                log.warn(
                    "Outcome unknown attaching artwork {} to movie {} ({}); keeping the file in case the attach committed",
                    artwork.id, movieId, e.status.code,
                )
            }
            throw e
        }

        if (change.hasPreviousArtworkId()) cleaner.deleteQuietly(change.previousArtworkId, "it was replaced on movie $movieId")
        return change.movie
    }

    companion object {
        /** Codes that mean the catalog answered and did not apply the change. */
        val DEFINITE_REJECTIONS = setOf(
            Status.Code.INVALID_ARGUMENT,
            Status.Code.NOT_FOUND,
            Status.Code.ALREADY_EXISTS,
            Status.Code.ABORTED,
            Status.Code.FAILED_PRECONDITION,
            Status.Code.OUT_OF_RANGE,
            Status.Code.PERMISSION_DENIED,
            Status.Code.UNAUTHENTICATED,
            Status.Code.UNIMPLEMENTED,
        )
    }
}
