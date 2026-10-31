package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.currentTimestamp
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

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
     * Called on every accepted change, so Hibernate always runs a version-checked UPDATE,
     * even when no field changed.
     */
    fun markUpdated() {
        updatedAt = maxOf(currentTimestamp(), updatedAt.plusNanos(1_000))
    }
}
