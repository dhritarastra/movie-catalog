package com.moviecatalog.media.artwork

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

data class Artwork(
    val id: UUID,
    val format: ImageFormat,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val sha256: String,
    val createdAt: Instant,
)

@Repository
class ArtworkRepository(private val jdbc: JdbcClient) {
    fun insert(id: UUID, format: ImageFormat, sizeBytes: Long, width: Int, height: Int, sha256: String): Artwork =
        jdbc.sql(
            """
            INSERT INTO artwork (id, content_type, size_bytes, width, height, sha256)
            VALUES (:id, :contentType, :sizeBytes, :width, :height, :sha256)
            RETURNING *
            """,
        )
            .param("id", id)
            .param("contentType", format.contentType)
            .param("sizeBytes", sizeBytes)
            .param("width", width)
            .param("height", height)
            .param("sha256", sha256)
            .query { rs, _ -> rs.toArtwork() }
            .single()

    fun findById(id: UUID): Artwork? =
        jdbc.sql("SELECT * FROM artwork WHERE id = :id")
            .param("id", id)
            .query { rs, _ -> rs.toArtwork() }
            .optional()
            .orElse(null)

    /** Returns whether a row existed. */
    fun delete(id: UUID): Boolean = jdbc.sql("DELETE FROM artwork WHERE id = :id").param("id", id).update() > 0
}

private fun ResultSet.toArtwork() =
    Artwork(
        id = getObject("id", UUID::class.java),
        format = ImageFormat.ofContentType(getString("content_type")),
        sizeBytes = getLong("size_bytes"),
        width = getInt("width"),
        height = getInt("height"),
        sha256 = getString("sha256"),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
    )
