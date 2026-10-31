package com.moviecatalog.media.artwork

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ArtworkRepository : JpaRepository<Artwork, UUID> {
    /**
     * A single DELETE that returns how many rows went (0 or 1). A derived delete would read the
     * row first and fail if another delete removed it in between.
     */
    @Transactional
    @Modifying
    @Query("delete from Artwork a where a.id = :id")
    fun removeById(id: UUID): Int
}
