package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.common.currentTimestamp
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A movie, mapped to the `movie` table created by V1__create_movie.sql. */
@Entity
@Table(name = "movie")
class Movie(
    var title: String,
    var synopsis: String?,
    var releaseDate: LocalDate?,
    var runtimeMinutes: Int?,
    // A Postgres text[] column.
    @field:JdbcTypeCode(SqlTypes.ARRAY)
    var genres: List<String>,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    // Owned by media-service, so there is no foreign key across the service boundary. It changes
    // only through MovieService.setArtwork and clearArtwork, never through an edit.
    var artworkId: UUID? = null

    /**
     * Starts at 1 and goes up by one with every change. Hibernate puts the version it read into
     * each UPDATE and DELETE, so a write that lost a race to another one fails instead of
     * overwriting it.
     */
    @Version
    var version: Int = 1
        protected set

    @Column(updatable = false)
    var createdAt: Instant = currentTimestamp()
        protected set

    var updatedAt: Instant = createdAt
        protected set

    /**
     * Mark an accepted mutation even when its submitted values are unchanged. Making the
     * timestamp dirty causes Hibernate to issue one version-checked UPDATE on flush.
     * Keep it distinct at Postgres microsecond precision even for two immediate writes.
     */
    @PreUpdate
    fun markUpdated() {
        updatedAt = maxOf(currentTimestamp(), updatedAt.plusNanos(1_000))
    }
}
