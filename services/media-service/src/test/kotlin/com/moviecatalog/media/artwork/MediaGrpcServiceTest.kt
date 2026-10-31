package com.moviecatalog.media.artwork

import com.google.protobuf.ByteString
import com.moviecatalog.media.TestcontainersConfiguration
import com.moviecatalog.media.v1.DeleteArtworkRequest
import com.moviecatalog.media.v1.DownloadArtworkRequest
import com.moviecatalog.media.v1.MediaServiceGrpc
import com.moviecatalog.media.v1.UploadArtworkRequest
import com.moviecatalog.media.v1.UploadMetadata
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.stub.StreamObserver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import com.moviecatalog.media.v1.Artwork as ArtworkProto
import com.moviecatalog.media.v1.ArtworkVariant as ArtworkVariantProto

@SpringBootTest(
    properties = [
        "spring.grpc.server.port=0",
        "spring.grpc.server.inprocess.name=$IN_PROCESS_NAME",
        "media.storage-dir=build/test-media",
        "media.max-upload-bytes=$MAX_BYTES",
    ],
)
@Import(TestcontainersConfiguration::class)
class MediaGrpcServiceTest {
    private val channel: ManagedChannel = InProcessChannelBuilder.forName(IN_PROCESS_NAME).directExecutor().build()
    private val async = MediaServiceGrpc.newStub(channel)
    private val blocking = MediaServiceGrpc.newBlockingStub(channel)

    @AfterEach
    fun closeChannel() {
        channel.shutdownNow()
    }

    @Test
    fun `stores an upload and streams back identical bytes`() {
        val png = TestImages.png(600, 300)

        val artwork = upload(png)

        assertThat(artwork.contentType).isEqualTo("image/png")
        assertThat(artwork.width).isEqualTo(600)
        assertThat(artwork.height).isEqualTo(300)
        assertThat(artwork.sizeBytes).isEqualTo(png.size.toLong())
        assertThat(download(artwork.id, ArtworkVariantProto.ARTWORK_VARIANT_FULL)).isEqualTo(png)
    }

    @Test
    fun `serves a thumbnail scaled to 400 pixels wide`() {
        val artwork = upload(TestImages.jpeg(1000, 500))

        val thumbnail = ImageIO.read(download(artwork.id, ArtworkVariantProto.ARTWORK_VARIANT_THUMBNAIL).inputStream())

        assertThat(thumbnail.width).isEqualTo(400)
        assertThat(thumbnail.height).isEqualTo(200)
    }

    @Test
    fun `rejects an SVG and an oversized upload with distinct codes`() {
        assertThat(uploadError(TestImages.svg).status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)

        val noise = TestImages.png(400, 400, noise = true)
        assertThat(noise.size.toLong()).isGreaterThan(MAX_BYTES)
        assertThat(uploadError(noise).status.code).isEqualTo(Status.Code.RESOURCE_EXHAUSTED)
    }

    @Test
    fun `delete removes the artwork and is safe to repeat`() {
        val artwork = upload(TestImages.png(20, 20))

        val first = blocking.deleteArtwork(DeleteArtworkRequest.newBuilder().setId(artwork.id).build())
        val second = blocking.deleteArtwork(DeleteArtworkRequest.newBuilder().setId(artwork.id).build())

        assertThat(first.existed).isTrue()
        assertThat(second.existed).isFalse()
        val error = assertThrows<StatusRuntimeException> { download(artwork.id, ArtworkVariantProto.ARTWORK_VARIANT_FULL) }
        assertThat(error.status.code).isEqualTo(Status.Code.NOT_FOUND)
    }

    @Test
    fun `reports unknown and malformed ids`() {
        val unknown = assertThrows<StatusRuntimeException> {
            download(UUID.randomUUID().toString(), ArtworkVariantProto.ARTWORK_VARIANT_FULL)
        }
        assertThat(unknown.status.code).isEqualTo(Status.Code.NOT_FOUND)

        val malformed = assertThrows<StatusRuntimeException> { download("nope", ArtworkVariantProto.ARTWORK_VARIANT_FULL) }
        assertThat(malformed.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
    }

    private fun upload(bytes: ByteArray): ArtworkProto =
        try {
            startUpload(bytes).get(10, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw e.cause!!
        }

    private fun uploadError(bytes: ByteArray): StatusRuntimeException =
        assertThrows<StatusRuntimeException> { upload(bytes) }

    private fun startUpload(bytes: ByteArray): CompletableFuture<ArtworkProto> {
        val result = CompletableFuture<ArtworkProto>()
        val requests = async.uploadArtwork(
            object : StreamObserver<ArtworkProto> {
                override fun onNext(value: ArtworkProto) {
                    result.complete(value)
                }

                override fun onError(t: Throwable) {
                    result.completeExceptionally(t)
                }

                override fun onCompleted() = Unit
            },
        )
        requests.onNext(
            UploadArtworkRequest.newBuilder()
                .setMetadata(UploadMetadata.newBuilder().setDeclaredContentType("image/png").setDeclaredSizeBytes(bytes.size.toLong()))
                .build(),
        )
        bytes.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (!result.isDone) {
                val read = input.read(buffer)
                if (read < 0) break
                requests.onNext(UploadArtworkRequest.newBuilder().setChunk(ByteString.copyFrom(buffer, 0, read)).build())
            }
        }
        if (!result.isDone) requests.onCompleted()
        return result
    }

    private fun download(id: String, variant: ArtworkVariantProto): ByteArray {
        val responses = blocking.downloadArtwork(DownloadArtworkRequest.newBuilder().setId(id).setVariant(variant).build())
        val metadata = responses.next().metadata
        val out = ByteArrayOutputStream()
        responses.forEachRemaining { it.chunk.writeTo(out) }
        assertThat(out.size().toLong()).isEqualTo(metadata.sizeBytes)
        return out.toByteArray()
    }
}

private const val IN_PROCESS_NAME = "media-grpc-test"
private const val MAX_BYTES = 300_000L
