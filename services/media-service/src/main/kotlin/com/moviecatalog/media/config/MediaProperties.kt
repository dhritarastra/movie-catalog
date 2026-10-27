package com.moviecatalog.media.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.nio.file.Path

/** `media.*` in application.yml. */
@Validated
@ConfigurationProperties("media")
data class MediaProperties(
    val storageDir: Path,
    /** Largest accepted upload. Exactly this many bytes is allowed; one more is rejected. */
    val maxUploadBytes: Long,
    /** Largest accepted dimensions, checked from the image header before any pixels are decoded. */
    val maxWidth: Int,
    val maxHeight: Int,
    val thumbnailWidth: Int,
    /** Size of each chunk a download is streamed in; gRPC's default message limit is 4 MiB. */
    @field:Min(1)
    @field:Max(1_048_576) // Leaves ample room for protobuf framing below the 4 MiB gRPC limit.
    val downloadChunkBytes: Int,
)
