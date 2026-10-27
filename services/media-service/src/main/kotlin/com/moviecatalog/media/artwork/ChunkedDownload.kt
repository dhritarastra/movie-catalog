package com.moviecatalog.media.artwork

import com.google.protobuf.ByteString
import com.moviecatalog.media.v1.DownloadArtworkResponse
import io.grpc.Status
import io.grpc.stub.ServerCallStreamObserver
import java.io.IOException
import java.io.InputStream

/**
 * Sends one download: the metadata message, then the file in chunks, but only while gRPC reports
 * the client can take more (`isReady`). When a slow client falls behind, sending pauses until gRPC
 * calls the on-ready handler again, so at most about one chunk is buffered instead of the whole
 * file queueing in memory. The file is closed on completion, read failure and cancellation.
 *
 * gRPC runs the on-ready and on-cancel handlers one at a time for a call, so this needs no locking.
 */
class ChunkedDownload(
    private val call: ServerCallStreamObserver<DownloadArtworkResponse>,
    private val metadata: DownloadArtworkResponse,
    private val input: InputStream,
    chunkBytes: Int = MediaGrpcService.CHUNK_BYTES,
) {
    private val buffer = ByteArray(chunkBytes)
    private var metadataSent = false

    @Volatile
    private var finished = false

    /** Must be called before the service method returns, as gRPC requires for these handlers. */
    fun start() {
        call.setOnCancelHandler { finish() }
        call.setOnReadyHandler { sendWhileReady() }
    }

    private fun sendWhileReady() {
        try {
            while (!finished && call.isReady) {
                if (!metadataSent) {
                    call.onNext(metadata)
                    metadataSent = true
                    continue
                }
                val read = input.read(buffer)
                if (read < 0) {
                    finish()
                    call.onCompleted()
                    return
                }
                call.onNext(DownloadArtworkResponse.newBuilder().setChunk(ByteString.copyFrom(buffer, 0, read)).build())
            }
        } catch (e: IOException) {
            finish()
            call.onError(Status.INTERNAL.withDescription("The artwork could not be read").withCause(e).asException())
        }
    }

    private fun finish() {
        finished = true
        runCatching { input.close() }
    }
}
