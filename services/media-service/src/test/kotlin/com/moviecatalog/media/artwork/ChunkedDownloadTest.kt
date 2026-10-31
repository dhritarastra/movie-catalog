package com.moviecatalog.media.artwork

import com.moviecatalog.media.v1.DownloadArtworkResponse
import com.moviecatalog.media.v1.DownloadMetadata
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.stub.ServerCallStreamObserver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class ChunkedDownloadTest {
    private val metadata = DownloadArtworkResponse.newBuilder().setMetadata(DownloadMetadata.newBuilder().setSizeBytes(10)).build()

    @Test
    fun `sends only while the client is ready and resumes when it is ready again`() {
        val call = FakeCall()
        val input = TrackedInput(ByteArray(10) { it.toByte() })
        ChunkedDownload(call, metadata, input, chunkBytes = 4).start()

        call.ready = true
        call.allowMessages(2) // metadata + one chunk, then the client is "busy"
        call.onReady()
        assertThat(call.sent).hasSize(2)
        assertThat(call.completed).isFalse()

        call.allowMessages(10)
        call.onReady()

        assertThat(call.sent.drop(1).map { it.chunk.size() }).containsExactly(4, 4, 2)
        assertThat(call.completed).isTrue()
        assertThat(input.closed).isTrue()
    }

    @Test
    fun `does nothing until gRPC says the call is ready`() {
        val call = FakeCall()
        ChunkedDownload(call, metadata, TrackedInput(ByteArray(10)), chunkBytes = 4).start()

        call.onReady()

        assertThat(call.sent).isEmpty()
    }

    @Test
    fun `stops sending and closes the file when the client cancels`() {
        val call = FakeCall()
        val input = TrackedInput(ByteArray(100))
        ChunkedDownload(call, metadata, input, chunkBytes = 4).start()
        call.ready = true
        call.allowMessages(2)
        call.onReady()

        call.cancel()
        call.allowMessages(100)
        call.onReady()

        assertThat(call.sent).hasSize(2)
        assertThat(input.closed).isTrue()
        assertThat(call.completed).isFalse()
    }

    @Test
    fun `reports a read failure as INTERNAL and closes the file`() {
        val call = FakeCall()
        val input = object : TrackedInput(ByteArray(0)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("disk gone")
        }
        ChunkedDownload(call, metadata, input, chunkBytes = 4).start()
        call.ready = true
        call.allowMessages(10)

        call.onReady()

        assertThat((call.error as StatusException).status.code).isEqualTo(Status.Code.INTERNAL)
        assertThat(input.closed).isTrue()
    }

    private open class TrackedInput(bytes: ByteArray) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var closed = false

        override fun read(): Int = delegate.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)

        override fun close() {
            closed = true
        }
    }

    /** Ready while the "client" still has room, like gRPC flow control. */
    private class FakeCall : ServerCallStreamObserver<DownloadArtworkResponse>() {
        val sent = mutableListOf<DownloadArtworkResponse>()
        var completed = false
        var error: Throwable? = null
        var ready = false
        private var room = 0
        private var cancelled = false
        private var onReadyHandler: Runnable? = null
        private var onCancelHandler: Runnable? = null

        fun allowMessages(count: Int) {
            room = count
        }

        fun onReady() = onReadyHandler?.run()

        fun cancel() {
            cancelled = true
            onCancelHandler?.run()
        }

        override fun isReady(): Boolean = ready && room > 0 && !cancelled

        override fun onNext(value: DownloadArtworkResponse) {
            check(!cancelled) { "sent after cancellation" }
            sent += value
            room--
        }

        override fun onError(t: Throwable) {
            error = t
        }

        override fun onCompleted() {
            completed = true
        }

        override fun isCancelled(): Boolean = cancelled

        override fun setOnCancelHandler(onCancelHandler: Runnable) {
            this.onCancelHandler = onCancelHandler
        }

        override fun setOnReadyHandler(onReadyHandler: Runnable) {
            this.onReadyHandler = onReadyHandler
        }

        override fun setCompression(compression: String) = Unit

        override fun disableAutoInboundFlowControl() = Unit

        override fun request(count: Int) = Unit

        override fun setMessageCompression(enable: Boolean) = Unit
    }
}
