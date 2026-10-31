package com.moviecatalog.media.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.nio.file.Path

@Validated
@ConfigurationProperties("media")
data class MediaProperties(
    val storageDir: Path,
    val maxUploadBytes: Long,
    // Checked from the image header before any pixels are decoded.
    val maxWidth: Int,
    val maxHeight: Int,
    val thumbnailWidth: Int,
    // Well under gRPC's 4 MiB message limit.
    @field:Min(1)
    @field:Max(1_048_576)
    val downloadChunkBytes: Int,
)
