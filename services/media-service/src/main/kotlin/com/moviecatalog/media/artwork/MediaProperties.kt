package com.moviecatalog.media.artwork

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path

@ConfigurationProperties("media")
data class MediaProperties(
    val storageDir: Path,
    /** Largest accepted upload. Exactly this many bytes is allowed; one more is rejected. */
    val maxUploadBytes: Long,
    /** Largest accepted dimensions, checked from the image header before any pixels are decoded. */
    val maxWidth: Int,
    val maxHeight: Int,
    val thumbnailWidth: Int,
)
