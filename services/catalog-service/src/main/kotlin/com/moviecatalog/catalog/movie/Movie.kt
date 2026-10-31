package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.common.currentTimestamp
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "movie")
class Movie(
    var title: String,
    var synopsis: String?,
    var releaseDate: LocalDate?,
    var runtimeMinutes: Int?,
    @field:JdbcTypeCode(SqlTypes.ARRAY)
    var genres: List<String>,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    // Owned by media-service, so there is no foreign key.
    var artworkId: UUID? = null

    // Hibernate checks this on every update and delete, so a stale write fails.
    @Version
    var version: Int = 1
        protected set

    @Column(updatable = false)
    var createdAt: Instant = currentTimestamp()
        protected set

    var updatedAt: Instant = createdAt
        protected set

    /**
     * Called on every accepted change, so Hibernate always runs a version-checked UPDATE,
     * even when no field changed.
     */
    fun markUpdated() {
        updatedAt = maxOf(currentTimestamp(), updatedAt.plusNanos(1_000))
    }
}
