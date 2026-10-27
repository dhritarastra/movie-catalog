package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.currentTimestamp
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A cast member or creator, mapped to the `person` table created by V2__create_person.sql.
 * Roles belong to credits, so the same person can be both. Versions work as for Movie.
 */
@Entity
@Table(name = "person")
class Person(
    var name: String,
    var bio: String?,
    var birthDate: LocalDate?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

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
     * JPA's OPTIMISTIC_FORCE_INCREMENT lock is not used instead: when a field does change, it
     * raises the version twice (once for the UPDATE, once for the forced increment).
     */
    @PreUpdate
    fun markUpdated() {
        updatedAt = maxOf(currentTimestamp(), updatedAt.plusNanos(1_000))
    }
}
