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

    /**
     * Deletes the credit if there is one and returns how many were deleted (0 or 1). One DELETE
     * statement, so two removes of the same credit cannot both find it and then race.
     */
    @Transactional
    @Modifying
    @Query("delete from Credit c where c.id = :id")
    fun removeById(id: UUID): Int
}
