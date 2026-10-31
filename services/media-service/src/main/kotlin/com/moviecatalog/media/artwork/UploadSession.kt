package com.moviecatalog.media.artwork

import com.moviecatalog.media.v1.UploadArtworkRequest
import io.grpc.StatusException
import io.grpc.stub.StreamObserver
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import com.moviecatalog.media.v1.Artwork as ArtworkProto

/**
 * Receives one upload: a metadata message, then chunks written straight to a temp file.
 * The size limit is checked on every chunk, so an oversized upload stops as soon as it crosses it.
 */
class UploadSession(
    private val storage: ArtworkStorage,
    private val service: ArtworkService,
    private val maxBytes: Long,
    private val response: StreamObserver<ArtworkProto>,
    private val toStatus: (Throwable) -> StatusException,
) : StreamObserver<UploadArtworkRequest> {
    private var file: Path? = null
    private var output: OutputStream? = null
    private val digest = MessageDigest.getInstance("SHA-256")
    private var received = 0L
    private var finished = false

    override fun onNext(message: UploadArtworkRequest) {
        if (finished) return
        try {
            when (message.payloadCase) {
                UploadArtworkRequest.PayloadCase.METADATA -> start(message)
                UploadArtworkRequest.PayloadCase.CHUNK -> write(message)
                else -> throw InvalidArtworkException("Each message must carry metadata or a chunk")
            }
        } catch (e: Exception) {
            fail(e)
        }
    }

    override fun onError(t: Throwable) {
        finished = true
        discard()
    }

    override fun onCompleted() {
        if (finished) return
        try {
            val out = output ?: throw InvalidArtworkException("The upload must start with a metadata message")
            out.close()
            if (received == 0L) throw InvalidArtworkException("The image is empty")
            val artwork = service.store(file!!, received, HexFormat.of().formatHex(digest.digest()))
            finished = true
            response.onNext(artwork.toProto())
            response.onCompleted()
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun start(message: UploadArtworkRequest) {
        if (output != null) throw InvalidArtworkException("Metadata must be sent once, as the first message")
        if (message.metadata.declaredSizeBytes > maxBytes) throw ArtworkTooLargeException(maxBytes)
        val path = storage.createTempFile()
        file = path
        output = Files.newOutputStream(path)
    }

    private fun write(message: UploadArtworkRequest) {
        val out = output ?: throw InvalidArtworkException("The upload must start with a metadata message")
        val chunk = message.chunk
        if (received + chunk.size() > maxBytes) throw ArtworkTooLargeException(maxBytes)
        chunk.writeTo(out)
        digest.update(chunk.asReadOnlyByteBuffer())
        received += chunk.size()
    }

    private fun fail(e: Exception) {
        finished = true
        discard()
        response.onError(toStatus(e))
    }

    private fun discard() {
        runCatching { output?.close() }
        file?.let { runCatching { Files.deleteIfExists(it) } }
    }
}
