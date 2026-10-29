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
 * A person's role on a movie, mapped to the `credit` table created by V3__create_credit.sql.
 * The movie and person are kept as plain IDs rather than JPA relations: credits are always
 * loaded in batches by movie or by person, and the database enforces the references.
 * Credits are last-write-wins, so there is no version.
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
