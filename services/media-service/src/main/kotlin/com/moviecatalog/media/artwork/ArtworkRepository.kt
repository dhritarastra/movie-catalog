package com.moviecatalog.media.artwork

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ArtworkRepository : JpaRepository<Artwork, UUID> {
    /**
     * Deletes the row if there is one and returns how many were deleted (0 or 1), so a repeated
     * delete can report that nothing existed. Its own write transaction commits before the
     * caller removes the files, so a row never outlives its files.
     */
    @Transactional
    fun removeById(id: UUID): Long
}
