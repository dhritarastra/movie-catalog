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

    fun getAll(ids: Collection<UUID>): List<Movie> = repository.findAllById(ids)

    /** Newest first, with id as a tie-breaker so pages stay stable. */
    fun list(offset: Int, limit: Int): Page<Movie> = repository.findAll(paging.of(offset, limit, NEWEST_FIRST))

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

    /** Returns the artwork the movie had, so the gateway can delete the file. */
    @Transactional
    fun delete(id: UUID, expectedVersion: Int): UUID? {
        val movie = current(id, expectedVersion)
        repository.delete(movie)
        repository.flush()
        return movie.artworkId
    }

    /** Returns the replaced artwork, read from the same version-checked row. */
    @Transactional
    fun setArtwork(id: UUID, expectedVersion: Int, artworkId: UUID): ArtworkChange = changeArtwork(id, expectedVersion, artworkId)

    @Transactional
    fun clearArtwork(id: UUID, expectedVersion: Int): ArtworkChange = changeArtwork(id, expectedVersion, null)

    private fun changeArtwork(id: UUID, expectedVersion: Int, artworkId: UUID?): ArtworkChange {
        val movie = current(id, expectedVersion)
        val previous = movie.artworkId
        movie.artworkId = artworkId
        movie.markUpdated()
        return ArtworkChange(repository.saveAndFlush(movie), previous)
    }

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
