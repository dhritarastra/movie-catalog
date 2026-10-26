package com.moviecatalog.gateway.grpc

import com.moviecatalog.gateway.GatewayProperties
import com.moviecatalog.media.v1.Artwork
import com.moviecatalog.media.v1.MediaServiceGrpc
import com.moviecatalog.media.v1.UploadArtworkRequest
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** The client side of the streaming upload, against a fake media server over in-process gRPC. */
class MediaClientTest {
    private val name = InProcessServerBuilder.generateName()
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    @AfterEach
    fun tearDown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun `sends metadata first, then the file in 64 KiB chunks that reassemble exactly`() {
        val fake = RecordingMediaService()
        val client = start(fake)
        val bytes = Random(7).nextBytes(300_000)

        val artwork = client.upload(UploadSource("image/png", bytes.size.toLong(), "poster.png") { bytes.inputStream() })

        assertThat(artwork.id).isEqualTo("stored")
        assertThat(fake.messages.first().hasMetadata()).isTrue()
        assertThat(fake.messages.first().metadata.declaredSizeBytes).isEqualTo(300_000)
        assertThat(fake.messages.drop(1).map { it.chunk.size() }).containsExactly(65_536, 65_536, 65_536, 65_536, 37_856)
        assertThat(fake.received.toByteArray()).isEqualTo(bytes)
    }

    @Test
    fun `stops sending and reports the error when media rejects the upload part-way`() {
        val fake = RecordingMediaService(rejectAfterChunks = 1)
        val client = start(fake)
        val bytes = ByteArray(1_000_000)

        val error = assertThrows<StatusRuntimeException> {
            client.upload(UploadSource("image/png", bytes.size.toLong(), "big.png") { bytes.inputStream() })
        }

        assertThat(error.status.code).isEqualTo(Status.Code.RESOURCE_EXHAUSTED)
        // Far fewer than the 16 chunks a full send would need; exact count depends on what was in flight.
        assertThat(fake.chunksSeen.get()).isLessThan(16)
    }

    private fun start(service: MediaServiceGrpc.MediaServiceImplBase): MediaClient {
        server = InProcessServerBuilder.forName(name).addService(service).build().start()
        channel = InProcessChannelBuilder.forName(name).build()
        return MediaClient(
            MediaServiceGrpc.newStub(channel),
            MediaServiceGrpc.newBlockingStub(channel),
            GatewayProperties(5_242_880, Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(10)),
        )
    }

    private class RecordingMediaService(private val rejectAfterChunks: Int? = null) : MediaServiceGrpc.MediaServiceImplBase() {
        val messages = mutableListOf<UploadArtworkRequest>()
        val received = ByteArrayOutputStream()
        val chunksSeen = AtomicInteger()

        override fun uploadArtwork(responseObserver: StreamObserver<Artwork>): StreamObserver<UploadArtworkRequest> =
            object : StreamObserver<UploadArtworkRequest> {
                private var failed = false

                override fun onNext(value: UploadArtworkRequest) {
                    if (failed) return
                    messages += value
                    if (value.hasChunk()) {
                        value.chunk.writeTo(received)
                        if (chunksSeen.incrementAndGet() == rejectAfterChunks) {
                            failed = true
                            responseObserver.onError(Status.RESOURCE_EXHAUSTED.withDescription("too large").asException())
                        }
                    }
                }

                override fun onError(t: Throwable) = Unit

                override fun onCompleted() {
                    if (failed) return
                    responseObserver.onNext(Artwork.newBuilder().setId("stored").setSizeBytes(received.size().toLong()).build())
                    responseObserver.onCompleted()
                }
            }
    }
}
