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
 * Media and catalog have no shared transaction, so:
 * - if the catalog refuses the new artwork, the uploaded file is deleted;
 * - if the outcome is unknown (timeout), the file is kept, since the catalog may have saved it;
 * - the replaced artwork is deleted using the ID the catalog returned.
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
        // The catalog answered and did not apply the change.
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
