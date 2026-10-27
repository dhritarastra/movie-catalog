package com.moviecatalog.gateway.movie

import com.moviecatalog.gateway.grpc.MediaClient
import com.moviecatalog.media.v1.ArtworkVariant
import io.grpc.Context
import io.grpc.Status
import io.grpc.StatusRuntimeException
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Serves image bytes over plain HTTP GET, so images can be cached like any other: GraphQL
 * returns the URL, and the browser loads it like any image. Bytes are streamed from media over
 * gRPC straight into the response, one chunk at a time.
 *
 * Each upload gets a new artwork ID, so the bytes behind a URL never change and successful
 * responses can be cached for a year. Errors are never cached, including ones that happen after
 * the success headers were prepared but before anything was sent.
 */
@RestController
class MediaController(private val media: MediaClient) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/media/{id}")
    fun image(
        @PathVariable id: String,
        @RequestParam(defaultValue = "full") variant: String,
        response: HttpServletResponse,
    ) {
        val grpcVariant = when (variant) {
            "full" -> ArtworkVariant.ARTWORK_VARIANT_FULL
            "thumb" -> ArtworkVariant.ARTWORK_VARIANT_THUMBNAIL
            else -> return error(response, HttpServletResponse.SC_BAD_REQUEST)
        }
        // The gRPC call runs in a cancellable context, so stopping early (an error, or the browser
        // going away) cancels it and media stops reading the file.
        val download = Context.current().withCancellation()
        try {
            download.run { relay(id, variant, grpcVariant, response) }
        } finally {
            download.cancel(null)
        }
    }

    private fun relay(id: String, variant: String, grpcVariant: ArtworkVariant, response: HttpServletResponse) {
        val stream = media.download(id, grpcVariant)
        try {
            val metadata = stream.next().metadata
            response.contentType = metadata.contentType
            response.setContentLengthLong(metadata.sizeBytes)
            response.setHeader(HttpHeaders.ETAG, "\"${metadata.sha256}-$variant\"")
            response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            response.setHeader("X-Content-Type-Options", "nosniff")
            val out = response.outputStream
            stream.forEachRemaining { it.chunk.writeTo(out) }
        } catch (e: StatusRuntimeException) {
            if (response.isCommitted) {
                // Headers and some bytes are already on their way, so the only honest signal left is
                // a truncated response: rethrowing aborts it, and the short body is never cached.
                log.warn("Artwork {} failed part-way through the download: {}", id, e.status)
                throw e
            }
            // Nothing sent yet: drop the cache headers set above, so the error is never cached.
            response.reset()
            error(response, statusFor(e, id))
        }
    }

    private fun statusFor(e: StatusRuntimeException, id: String): Int =
        when (e.status.code) {
            // A malformed ID cannot name an image either, so both are simply "not found".
            Status.Code.NOT_FOUND, Status.Code.INVALID_ARGUMENT -> HttpServletResponse.SC_NOT_FOUND
            Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> HttpServletResponse.SC_SERVICE_UNAVAILABLE
            else -> {
                log.error("Downloading artwork {} failed", id, e)
                HttpServletResponse.SC_BAD_GATEWAY
            }
        }

    private fun error(response: HttpServletResponse, status: Int) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.sendError(status)
    }
}
