package com.moviecatalog.media.artwork

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Metadata for a stored image; the files are on disk. Artwork never changes, so it has no version. */
@Entity
@Table(name = "artwork")
class Artwork(
    // Assigned, not generated: the files are stored under this id before the row is written.
    @field:Id
    val id: UUID,
    @field:Column(name = "content_type")
    @field:Convert(converter = ImageFormatConverter::class)
    val format: ImageFormat,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    // Used by the gateway as the HTTP ETag.
    val sha256: String,
) {
    // Rounded to microseconds, which is what Postgres stores.
    @Column(updatable = false)
    var createdAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
        protected set
}
