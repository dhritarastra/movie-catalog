package com.moviecatalog.catalog.movie

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
     * Attaches artwork if the movie is still at [expectedVersion]. The previous artwork ID is read
     * under the same row lock as the update, so the gateway deletes exactly the file that was
     * replaced, never one chosen from an earlier, possibly stale read (PLAN.md §6).
     */
    @Transactional
    fun setArtwork(id: UUID, expectedVersion: Int, artworkId: UUID): ArtworkChange {
        val current = repository.lockArtworkState(id) ?: throw notFound(id)
        if (current.version != expectedVersion) {
            throw VersionConflictException(
                "Movie $id is at version ${current.version}, not $expectedVersion; reload and try again",
            )
        }
        return ArtworkChange(repository.updateArtwork(id, artworkId), current.artworkId)
    }

    private fun notFound(id: UUID) = NotFoundException("Movie $id not found")
}
