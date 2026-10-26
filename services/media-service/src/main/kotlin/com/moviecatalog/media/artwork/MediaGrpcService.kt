package com.moviecatalog.media.artwork

import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import com.moviecatalog.media.v1.DeleteArtworkRequest
import com.moviecatalog.media.v1.DeleteArtworkResponse
import com.moviecatalog.media.v1.DownloadArtworkRequest
import com.moviecatalog.media.v1.DownloadArtworkResponse
import com.moviecatalog.media.v1.DownloadMetadata
import com.moviecatalog.media.v1.MediaServiceGrpc
import com.moviecatalog.media.v1.UploadArtworkRequest
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.util.UUID
import com.moviecatalog.media.v1.Artwork as ArtworkProto
import com.moviecatalog.media.v1.ArtworkVariant as ArtworkVariantProto

/** gRPC endpoint for artwork. Unary errors are mapped by [MediaGrpcExceptionHandler]. */
@Service
class MediaGrpcService(
    private val artwork: ArtworkService,
    private val storage: ArtworkStorage,
    private val properties: MediaProperties,
    private val exceptionHandler: MediaGrpcExceptionHandler,
) : MediaServiceGrpc.MediaServiceImplBase() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun uploadArtwork(responseObserver: StreamObserver<ArtworkProto>): StreamObserver<UploadArtworkRequest> =
        UploadSession(storage, artwork, properties.maxUploadBytes, responseObserver, ::toStatus)

    override fun downloadArtwork(request: DownloadArtworkRequest, responseObserver: StreamObserver<DownloadArtworkResponse>) {
        val variant = if (request.variant == ArtworkVariantProto.ARTWORK_VARIANT_THUMBNAIL) ArtworkVariant.THUMBNAIL else ArtworkVariant.FULL
        val file = artwork.open(parseId(request.id), variant)
        responseObserver.onNext(
            DownloadArtworkResponse.newBuilder()
                .setMetadata(
                    DownloadMetadata.newBuilder()
                        .setContentType(file.artwork.format.contentType)
                        .setSizeBytes(file.sizeBytes)
                        .setSha256(file.artwork.sha256),
                )
                .build(),
        )
        Files.newInputStream(file.path).use { input ->
            val buffer = ByteArray(CHUNK_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                responseObserver.onNext(DownloadArtworkResponse.newBuilder().setChunk(ByteString.copyFrom(buffer, 0, read)).build())
            }
        }
        responseObserver.onCompleted()
    }

    override fun deleteArtwork(request: DeleteArtworkRequest, responseObserver: StreamObserver<DeleteArtworkResponse>) {
        val existed = artwork.delete(parseId(request.id))
        responseObserver.onNext(DeleteArtworkResponse.newBuilder().setExisted(existed).build())
        responseObserver.onCompleted()
    }

    private fun parseId(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrNull()?.takeIf { it.toString() == value.lowercase() }
            ?: throw InvalidArtworkException("id must be a UUID")

    private fun toStatus(e: Throwable): StatusException =
        exceptionHandler.handleException(e) ?: run {
            log.error("Upload failed", e)
            Status.INTERNAL.withDescription("The upload could not be stored").asException()
        }

    companion object {
        const val CHUNK_BYTES = 64 * 1024
    }
}

fun Artwork.toProto(): ArtworkProto =
    ArtworkProto.newBuilder()
        .setId(id.toString())
        .setContentType(format.contentType)
        .setSizeBytes(sizeBytes)
        .setWidth(width)
        .setHeight(height)
        .setSha256(sha256)
        .setCreatedAt(Timestamp.newBuilder().setSeconds(createdAt.epochSecond).setNanos(createdAt.nano))
        .build()
