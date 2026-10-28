package com.moviecatalog.media.artwork

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ArtworkRepository : JpaRepository<Artwork, UUID> {
    /**
     * Deletes the row if there is one and returns how many were deleted (0 or 1), so a repeated
     * delete can report that nothing existed. One DELETE statement, run at once: a delete derived
     * from the method name would read the row first and fail at commit if another delete removed
     * it in between. Its own write transaction commits before the caller removes the files, so a
     * row never outlives its files.
     */
    @Transactional
    @Modifying
    @Query("delete from Artwork a where a.id = :id")
    fun removeById(id: UUID): Int
}
