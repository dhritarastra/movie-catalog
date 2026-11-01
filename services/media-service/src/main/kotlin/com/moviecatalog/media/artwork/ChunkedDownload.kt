package com.moviecatalog.media.artwork

import com.google.protobuf.ByteString
import com.moviecatalog.media.v1.DownloadArtworkResponse
import io.grpc.Status
import io.grpc.stub.ServerCallStreamObserver
import java.io.IOException
import java.io.InputStream

/**
 * Streams a file in chunks, but only while the client is ready for more (isReady), so a slow
 * client never makes the whole file pile up in memory.
 */
class ChunkedDownload(
    private val call: ServerCallStreamObserver<DownloadArtworkResponse>,
    private val metadata: DownloadArtworkResponse,
    private val input: InputStream,
    chunkBytes: Int,
) {
    private val buffer = ByteArray(chunkBytes)
    private var metadataSent = false

    @Volatile
    private var finished = false

    /** gRPC requires the handlers to be set before the service method returns. */
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
