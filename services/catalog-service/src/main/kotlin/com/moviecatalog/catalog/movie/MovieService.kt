package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.common.NotFoundException
import com.moviecatalog.catalog.common.Paging
import com.moviecatalog.catalog.common.VersionConflictException
import com.moviecatalog.catalog.common.validation.InputValidator
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MovieService(
    private val repository: MovieRepository,
    private val validator: InputValidator,
    private val paging: Paging,
) {
    @Transactional
    fun create(input: MovieInput): Movie {
        validator.check(input)
        return repository.save(
            Movie(input.title, input.synopsis, input.releaseDate?.let(LocalDate::parse), input.runtimeMinutes, input.genres),
        )
    }

    fun get(id: UUID): Movie = repository.findByIdOrNull(id) ?: throw notFound(id)

    /** The movies that exist among [ids]; unknown IDs are left out. */
    fun getAll(ids: Collection<UUID>): List<Movie> = repository.findAllById(ids)

    /** Newest first; the id tie-breaker keeps the order stable across pages. */
    fun list(offset: Int, limit: Int): Page<Movie> = repository.findAll(paging.of(offset, limit, NEWEST_FIRST))

    /** Replaces every editable field with the input (a missing optional field clears it) if the movie is still at [expectedVersion]. */
    @Transactional
    fun update(id: UUID, expectedVersion: Int, input: MovieInput): Movie {
        validator.check(input)
        val movie = current(id, expectedVersion)
        movie.title = input.title
        movie.synopsis = input.synopsis
        movie.releaseDate = input.releaseDate?.let(LocalDate::parse)
        movie.runtimeMinutes = input.runtimeMinutes
        movie.genres = input.genres
        movie.markUpdated()
        return repository.saveAndFlush(movie)
    }

    /** Deletes the movie if it is still at [expectedVersion], returning the artwork it had so the gateway can delete the file. */
    @Transactional
    fun delete(id: UUID, expectedVersion: Int): UUID? {
        val movie = current(id, expectedVersion)
        repository.delete(movie)
        repository.flush()
        return movie.artworkId
    }

    /**
     * Attaches artwork if the movie is still at [expectedVersion]. The previous artwork ID comes
     * from the same row the update is checked against, so the gateway deletes exactly the file
     * that was replaced, never one chosen from an earlier, possibly stale read.
     */
    @Transactional
    fun setArtwork(id: UUID, expectedVersion: Int, artworkId: UUID): ArtworkChange = changeArtwork(id, expectedVersion, artworkId)

    /** Removes the movie's artwork, with the same version check, returning the artwork that was removed. */
    @Transactional
    fun clearArtwork(id: UUID, expectedVersion: Int): ArtworkChange = changeArtwork(id, expectedVersion, null)

    private fun changeArtwork(id: UUID, expectedVersion: Int, artworkId: UUID?): ArtworkChange {
        val movie = current(id, expectedVersion)
        val previous = movie.artworkId
        movie.artworkId = artworkId
        movie.markUpdated()
        return ArtworkChange(repository.saveAndFlush(movie), previous)
    }

    /**
     * The movie the caller wants to change, if they saw its current version. A change made by
     * someone else between this read and the write is caught by Hibernate's version check.
     */
    private fun current(id: UUID, expectedVersion: Int): Movie {
        val movie = repository.findByIdOrNull(id) ?: throw notFound(id)
        if (movie.version != expectedVersion) {
            throw VersionConflictException("Movie $id is at version ${movie.version}, not $expectedVersion; reload and try again")
        }
        return movie
    }

    private fun notFound(id: UUID) = NotFoundException("Movie $id not found")

    private companion object {
        val NEWEST_FIRST: Sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
    }
}
