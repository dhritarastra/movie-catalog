package com.moviecatalog.media.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path

/** `media.*` in application.yml. */
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
    val downloadChunkBytes: Int,
)
