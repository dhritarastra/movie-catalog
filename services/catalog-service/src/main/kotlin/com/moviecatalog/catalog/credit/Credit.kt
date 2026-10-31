package com.moviecatalog.catalog.credit

import com.moviecatalog.catalog.common.currentTimestamp
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Movie and person are plain IDs because credits are always loaded in batches.
 * There is no version: the last write wins.
 */
@Entity
@Table(name = "credit")
class Credit(
    @field:Column(updatable = false)
    val movieId: UUID,
    @field:Column(updatable = false)
    val personId: UUID,
    @field:Enumerated(EnumType.STRING)
    var type: CreditType,
    var characterName: String?,
    var billingOrder: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    @Column(updatable = false)
    var createdAt: Instant = currentTimestamp()
        protected set
}
