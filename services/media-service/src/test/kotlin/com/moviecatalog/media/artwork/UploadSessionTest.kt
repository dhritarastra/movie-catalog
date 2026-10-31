package com.moviecatalog.media.artwork

import com.google.protobuf.ByteString
import com.moviecatalog.media.config.MediaProperties
import com.moviecatalog.media.v1.UploadArtworkRequest
import com.moviecatalog.media.v1.UploadMetadata
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.stub.StreamObserver
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID
import com.moviecatalog.media.v1.Artwork as ArtworkProto

class UploadSessionTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var storage: ArtworkStorage
    private val service = mockk<ArtworkService>()
    private val response = RecordingObserver()
    private val handler = MediaGrpcExceptionHandler()

    @BeforeEach
    fun setUp() {
        storage = ArtworkStorage(MediaProperties(dir, MAX_BYTES, 100, 100, 10, 64 * 1024))
        every { service.store(any(), any(), any()) } answers {
            Artwork(UUID.randomUUID(), ImageFormat.PNG, secondArg(), 1, 1, thirdArg())
        }
    }

    private fun session() = UploadSession(storage, service, MAX_BYTES, response) { handler.handleException(it)!! }

    @Test
    fun `stores an upload sent in several chunks, with its size and SHA-256`() {
        val bytes = ByteArray(250) { it.toByte() }
        val stored = slot<Path>()
        every { service.store(capture(stored), any(), any()) } answers {
            assertThat(Files.readAllBytes(stored.captured)).isEqualTo(bytes)
            Artwork(UUID.randomUUID(), ImageFormat.PNG, secondArg(), 1, 1, thirdArg())
        }

        session().apply {
            onNext(metadata())
            bytes.toList().chunked(100).forEach { onNext(chunk(it.toByteArray())) }
            onCompleted()
        }

        assertThat(response.error).isNull()
        assertThat(response.value!!.sizeBytes).isEqualTo(250)
        assertThat(response.value!!.sha256).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
    }

    @Test
    fun `accepts exactly the size limit`() {
        session().apply {
            onNext(metadata())
            onNext(chunk(ByteArray(MAX_BYTES.toInt())))
            onCompleted()
        }

        assertThat(response.error).isNull()
        verify { service.store(any(), MAX_BYTES, any()) }
    }

    @Test
    fun `rejects the chunk that crosses the limit, ignores the rest and deletes the partial file`() {
        session().apply {
            onNext(metadata())
            onNext(chunk(ByteArray(MAX_BYTES.toInt())))
            onNext(chunk(ByteArray(1)))
            onNext(chunk(ByteArray(10)))
            onCompleted()
        }

        assertThat(response.code()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED)
        assertThat(response.errors).isEqualTo(1)
        assertThat(tempFiles()).isEmpty()
        verify(exactly = 0) { service.store(any(), any(), any()) }
    }

    @Test
    fun `rejects a declared size over the limit before any bytes arrive`() {
        session().onNext(metadata(declaredSize = MAX_BYTES + 1))

        assertThat(response.code()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED)
    }

    @Test
    fun `requires metadata first and only once`() {
        session().onNext(chunk(ByteArray(10)))
        assertThat(response.code()).isEqualTo(Status.Code.INVALID_ARGUMENT)

        val second = RecordingObserver()
        UploadSession(storage, service, MAX_BYTES, second) { handler.handleException(it)!! }.apply {
            onNext(metadata())
            onNext(metadata())
        }
        assertThat(second.code()).isEqualTo(Status.Code.INVALID_ARGUMENT)
        assertThat(tempFiles()).isEmpty()
    }

    @Test
    fun `rejects an upload with no bytes or no messages`() {
        session().apply {
            onNext(metadata())
            onCompleted()
        }
        assertThat(response.code()).isEqualTo(Status.Code.INVALID_ARGUMENT)

        val nothing = RecordingObserver()
        UploadSession(storage, service, MAX_BYTES, nothing) { handler.handleException(it)!! }.onCompleted()
        assertThat(nothing.code()).isEqualTo(Status.Code.INVALID_ARGUMENT)
    }

    @Test
    fun `a cancelled upload deletes the partial file and stores nothing`() {
        session().apply {
            onNext(metadata())
            onNext(chunk(ByteArray(100)))
            onError(Status.CANCELLED.asException())
        }

        assertThat(tempFiles()).isEmpty()
        assertThat(response.value).isNull()
        verify(exactly = 0) { service.store(any(), any(), any()) }
    }

    private fun tempFiles(): List<Path> = Files.list(dir.resolve("tmp")).use { it.toList() }

    private fun metadata(declaredSize: Long = 0) =
        UploadArtworkRequest.newBuilder()
            .setMetadata(UploadMetadata.newBuilder().setDeclaredContentType("image/png").setDeclaredSizeBytes(declaredSize))
            .build()

    private fun chunk(bytes: ByteArray) = UploadArtworkRequest.newBuilder().setChunk(ByteString.copyFrom(bytes)).build()

    private class RecordingObserver : StreamObserver<ArtworkProto> {
        var value: ArtworkProto? = null
        var error: Throwable? = null
        var errors = 0

        override fun onNext(value: ArtworkProto) {
            this.value = value
        }

        override fun onError(t: Throwable) {
            error = t
            errors++
        }

        override fun onCompleted() = Unit

        fun code(): Status.Code? = (error as? StatusException)?.status?.code
    }

    companion object {
        const val MAX_BYTES = 1000L
    }
}
