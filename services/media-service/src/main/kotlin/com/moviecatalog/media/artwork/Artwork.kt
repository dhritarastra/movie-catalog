package com.moviecatalog.media.artwork

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Metadata for one stored image, mapped to the `artwork` table created by V1__create_artwork.sql.
 * The bytes live on disk (ArtworkStorage). Artwork never changes once stored, so it has no version.
 */
@Entity
@Table(name = "artwork")
class Artwork(
    // Assigned by ArtworkService rather than generated: the files are stored under this id
    // before the row is written.
    @field:Id
    val id: UUID,
    @field:Column(name = "content_type")
    @field:Convert(converter = ImageFormatConverter::class)
    val format: ImageFormat,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    /** Hash of the original file; the gateway uses it in the HTTP ETag. */
    val sha256: String,
) {
    // Microseconds, the precision Postgres stores, so the value read back equals the one written.
    @Column(updatable = false)
    var createdAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
        protected set
}
