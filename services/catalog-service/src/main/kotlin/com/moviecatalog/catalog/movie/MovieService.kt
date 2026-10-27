package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.common.CatalogException
import com.moviecatalog.catalog.common.NotFoundException
import com.moviecatalog.catalog.common.Page
import com.moviecatalog.catalog.common.VersionConflictException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MovieService(private val repository: MovieRepository) {
    fun create(input: MovieInput): Movie = repository.insert(MovieValidator.validate(input))

    fun get(id: UUID): Movie = repository.findById(id) ?: throw notFound(id)

    @Transactional(readOnly = true)
    fun list(page: Page): MoviePage = MoviePage(repository.findPage(page), repository.count())

    /**
     * Replaces every editable field (DECISIONS D5) if the movie is still at [expectedVersion].
     * The version check is part of the UPDATE itself, so two concurrent edits cannot both win.
     */
    @Transactional
    fun update(id: UUID, expectedVersion: Int, input: MovieInput): Movie {
        val draft = MovieValidator.validate(input)
        return repository.update(id, expectedVersion, draft) ?: throw missingOrStale(id, expectedVersion)
    }

    /** Deletes the movie if it is still at [expectedVersion], returning the artwork it had for clean-up. */
    @Transactional
    fun delete(id: UUID, expectedVersion: Int): DeletedMovie =
        repository.delete(id, expectedVersion) ?: throw missingOrStale(id, expectedVersion)

    /**
     * Attaches artwork if the movie is still at [expectedVersion]. The previous artwork ID is read
     * under the same row lock as the update, so the gateway deletes exactly the file that was
     * replaced, never one chosen from an earlier, possibly stale read (PLAN.md §6).
     */
    @Transactional
    fun setArtwork(id: UUID, expectedVersion: Int, artworkId: UUID): ArtworkChange = changeArtwork(id, expectedVersion, artworkId)

    /** Removes the movie's artwork, with the same version check, returning the artwork that was removed. */
    @Transactional
    fun clearArtwork(id: UUID, expectedVersion: Int): ArtworkChange = changeArtwork(id, expectedVersion, null)

    private fun changeArtwork(id: UUID, expectedVersion: Int, artworkId: UUID?): ArtworkChange {
        val current = repository.lockArtworkState(id) ?: throw notFound(id)
        if (current.version != expectedVersion) throw conflict(id, current.version, expectedVersion)
        return ArtworkChange(repository.updateArtwork(id, artworkId), current.artworkId)
    }

    /** After a versioned write matched no row: either the movie is gone or someone changed it first. */
    private fun missingOrStale(id: UUID, expectedVersion: Int): CatalogException {
        val currentVersion = repository.findVersion(id) ?: return notFound(id)
        return conflict(id, currentVersion, expectedVersion)
    }

    private fun conflict(id: UUID, currentVersion: Int, expectedVersion: Int) =
        VersionConflictException("Movie $id is at version $currentVersion, not $expectedVersion; reload and try again")

    private fun notFound(id: UUID) = NotFoundException("Movie $id not found")
}
