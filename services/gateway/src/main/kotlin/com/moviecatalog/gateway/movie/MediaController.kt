package com.moviecatalog.gateway.movie

import com.moviecatalog.gateway.grpc.MediaClient
import com.moviecatalog.media.v1.ArtworkVariant
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
 * Serves image bytes over plain HTTP GET (docs/DECISIONS.md, PLAN.md §3 decision 3): GraphQL
 * returns the URL, and the browser loads it like any image. Bytes are streamed from media over
 * gRPC straight into the response, one chunk at a time.
 *
 * Each upload gets a new artwork ID, so the bytes behind a URL never change and successful
 * responses can be cached for a year. Errors are never cached.
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
        val stream = media.download(id, grpcVariant)
        val metadata = try {
            stream.next().metadata
        } catch (e: StatusRuntimeException) {
            return when (e.status.code) {
                // A malformed ID cannot name an image either, so both are simply "not found".
                Status.Code.NOT_FOUND, Status.Code.INVALID_ARGUMENT -> error(response, HttpServletResponse.SC_NOT_FOUND)
                Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE)
                else -> {
                    log.error("Downloading artwork {} failed", id, e)
                    error(response, HttpServletResponse.SC_BAD_GATEWAY)
                }
            }
        }

        response.contentType = metadata.contentType
        response.setContentLengthLong(metadata.sizeBytes)
        response.setHeader(HttpHeaders.ETAG, "\"${metadata.sha256}-$variant\"")
        response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
        response.setHeader("X-Content-Type-Options", "nosniff")
        val out = response.outputStream
        // If media fails after this point the headers are already sent, so the connection is aborted.
        stream.forEachRemaining { it.chunk.writeTo(out) }
    }

    private fun error(response: HttpServletResponse, status: Int) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.sendError(status)
    }
}
