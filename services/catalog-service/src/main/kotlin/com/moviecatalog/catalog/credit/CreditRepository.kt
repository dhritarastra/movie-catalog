package com.moviecatalog.catalog.credit

import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface CreditRepository : JpaRepository<Credit, UUID> {
    fun findAllByMovieIdIn(movieIds: Collection<UUID>, sort: Sort): List<Credit>

    fun findAllByPersonIdIn(personIds: Collection<UUID>, sort: Sort): List<Credit>

    /** One DELETE statement, so two removes of the same credit cannot both succeed. */
    @Transactional
    @Modifying
    @Query("delete from Credit c where c.id = :id")
    fun removeById(id: UUID): Int
}
