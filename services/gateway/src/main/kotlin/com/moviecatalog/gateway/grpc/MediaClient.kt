package com.moviecatalog.gateway.grpc

import com.google.protobuf.ByteString
import com.moviecatalog.gateway.config.GatewayProperties
import com.moviecatalog.media.v1.Artwork
import com.moviecatalog.media.v1.ArtworkVariant
import com.moviecatalog.media.v1.DeleteArtworkRequest
import com.moviecatalog.media.v1.DownloadArtworkRequest
import com.moviecatalog.media.v1.DownloadArtworkResponse
import com.moviecatalog.media.v1.MediaServiceGrpc
import com.moviecatalog.media.v1.UploadArtworkRequest
import com.moviecatalog.media.v1.UploadMetadata
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.ClientResponseObserver
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

@Component
class MediaClient(
    private val async: MediaServiceGrpc.MediaServiceStub,
    private val blocking: MediaServiceGrpc.MediaServiceBlockingStub,
    private val properties: GatewayProperties,
) {
    /**
     * Writes chunks only while the call is ready for more, so a slow media service slows the upload
     * down instead of the whole file being buffered in memory.
     */
    fun upload(source: UploadSource): Artwork {
        val result = CompletableFuture<Artwork>()
        val stub = async.withDeadlineAfter(properties.mediaUploadDeadline.toMillis(), TimeUnit.MILLISECONDS)
        stub.uploadArtwork(ChunkWriter(source, result, properties.mediaUploadChunkBytes))
        return try {
            // The deadline ends the call; the extra time lets its error reach this thread.
            result.get(properties.mediaUploadDeadline.toMillis() + 5_000, TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            throw e.cause as? StatusRuntimeException ?: Status.INTERNAL.withCause(e.cause).asRuntimeException()
        }
    }

    /** Errors show up while the stream is read. */
    fun download(id: String, variant: ArtworkVariant): Iterator<DownloadArtworkResponse> =
        blocking.withDeadlineAfter(properties.mediaDownloadDeadline.toMillis(), TimeUnit.MILLISECONDS)
            .downloadArtwork(DownloadArtworkRequest.newBuilder().setId(id).setVariant(variant).build())

    fun delete(id: String): Boolean =
        blocking.withDeadlineAfter(properties.catalogDeadline.toMillis(), TimeUnit.MILLISECONDS)
            .deleteArtwork(DeleteArtworkRequest.newBuilder().setId(id).build())
            .existed

    private class ChunkWriter(
        private val source: UploadSource,
        private val result: CompletableFuture<Artwork>,
        chunkBytes: Int,
    ) : ClientResponseObserver<UploadArtworkRequest, Artwork> {
        private val buffer = ByteArray(chunkBytes)
        private var input: InputStream? = null

        @Volatile
        private var done = false

        override fun beforeStart(requests: ClientCallStreamObserver<UploadArtworkRequest>) {
            requests.setOnReadyHandler {
                try {
                    while (requests.isReady && !done) writeNext(requests)
                } catch (e: IOException) {
                    done = true
                    closeInput()
                    requests.onError(Status.CANCELLED.withDescription("Could not read the upload").withCause(e).asException())
                    result.completeExceptionally(Status.INTERNAL.withDescription("Could not read the upload").asRuntimeException())
                }
            }
        }

        private fun writeNext(requests: ClientCallStreamObserver<UploadArtworkRequest>) {
            val stream = input
            if (stream == null) {
                input = source.open()
                requests.onNext(
                    UploadArtworkRequest.newBuilder()
                        .setMetadata(
                            UploadMetadata.newBuilder()
                                .setDeclaredContentType(source.contentType)
                                .setDeclaredSizeBytes(source.sizeBytes)
                                .setOriginalFilename(source.filename),
                        )
                        .build(),
                )
                return
            }
            val read = stream.read(buffer)
            if (read < 0) {
                done = true
                closeInput()
                requests.onCompleted()
            } else {
                requests.onNext(UploadArtworkRequest.newBuilder().setChunk(ByteString.copyFrom(buffer, 0, read)).build())
            }
        }

        override fun onNext(value: Artwork) {
            result.complete(value)
        }

        override fun onError(t: Throwable) {
            done = true
            closeInput()
            result.completeExceptionally(t)
        }

        override fun onCompleted() = Unit

        private fun closeInput() {
            runCatching { input?.close() }
        }
    }
}
