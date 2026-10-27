package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.common.InvalidArgumentException
import com.moviecatalog.catalog.common.NotFoundException
import com.moviecatalog.catalog.common.Paging
import com.moviecatalog.catalog.common.VersionConflictException
import com.moviecatalog.catalog.common.validation.inputValidatorOn
import com.moviecatalog.catalog.config.PagingProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class MovieServiceTest {
    private val repository = mockk<MovieRepository>(relaxUnitFun = true)
    private val service = MovieService(repository, inputValidatorOn(LocalDate.of(2026, 10, 28)), Paging(PagingProperties(maxLimit = 100)))

    private val id = UUID.randomUUID()
    private val input = MovieInput("Arrival", "Heptapods.", "2016-11-11", 116, listOf("Sci-Fi"))

    init {
        every { repository.save(any()) } answers { firstArg() }
        every { repository.saveAndFlush(any()) } answers { firstArg() }
    }

    @Test
    fun `create stores the input with the date parsed`() {
        val created = service.create(input)

        assertThat(created.title).isEqualTo("Arrival")
        assertThat(created.releaseDate).isEqualTo(LocalDate.of(2016, 11, 11))
        assertThat(created.genres).containsExactly("Sci-Fi")
        verify { repository.save(created) }
    }

    @Test
    fun `invalid input is rejected before anything is stored`() {
        assertThrows<InvalidArgumentException> { service.create(input.copy(title = "")) }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `get reports an unknown movie as not found`() {
        every { repository.findById(id) } returns Optional.empty()

        assertThrows<NotFoundException> { service.get(id) }
    }

    @Test
    fun `list asks for newest first, with the id as tie-breaker, from the requested offset`() {
        val pageable = slot<Pageable>()
        every { repository.findAll(capture(pageable)) } returns PageImpl(emptyList())

        val page: Page<Movie> = service.list(20, 10)

        assertThat(page.content).isEmpty()
        assertThat(pageable.captured.offset).isEqualTo(20)
        assertThat(pageable.captured.pageSize).isEqualTo(10)
        assertThat(pageable.captured.sort).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
    }

    @Test
    fun `invalid paging never reaches the database`() {
        assertThrows<InvalidArgumentException> { service.list(0, 0) }

        verify(exactly = 0) { repository.findAll(any<Pageable>()) }
    }

    @Test
    fun `an update replaces every field, clearing omitted ones, and keeps the artwork`() {
        val artworkId = UUID.randomUUID()
        val stored = Movie("Old", "Old synopsis", LocalDate.of(2000, 1, 1), 90, listOf("Drama")).apply { this.artworkId = artworkId }
        every { repository.findById(id) } returns Optional.of(stored)

        val updated = service.update(id, 1, MovieInput("New", null, null, null, emptyList()))

        assertThat(updated.title).isEqualTo("New")
        assertThat(updated.synopsis).isNull()
        assertThat(updated.releaseDate).isNull()
        assertThat(updated.runtimeMinutes).isNull()
        assertThat(updated.genres).isEmpty()
        assertThat(updated.artworkId).isEqualTo(artworkId)
        verify { repository.saveAndFlush(stored) }
    }

    @Test
    fun `an update from a stale version is a conflict and changes nothing`() {
        val stored = Movie("Theirs", null, null, null, emptyList())
        every { repository.findById(id) } returns Optional.of(stored)

        assertThrows<VersionConflictException> { service.update(id, 2, input) }

        assertThat(stored.title).isEqualTo("Theirs")
        verify(exactly = 0) { repository.saveAndFlush(any()) }
    }

    @Test
    fun `an update checks the input before looking the movie up, and reports an unknown movie`() {
        assertThrows<InvalidArgumentException> { service.update(id, 1, input.copy(runtimeMinutes = 0)) }
        verify(exactly = 0) { repository.findById(any()) }

        every { repository.findById(id) } returns Optional.empty()
        assertThrows<NotFoundException> { service.update(id, 1, input) }
    }

    @Test
    fun `delete removes the movie and returns its artwork so the file can be deleted`() {
        val artworkId = UUID.randomUUID()
        val stored = Movie("Arrival", null, null, null, emptyList()).apply { this.artworkId = artworkId }
        every { repository.findById(id) } returns Optional.of(stored)

        assertThat(service.delete(id, 1)).isEqualTo(artworkId)

        verify { repository.delete(stored) }
        verify { repository.flush() }
    }

    @Test
    fun `delete from a stale version keeps the movie, and an unknown movie is not found`() {
        every { repository.findById(id) } returns Optional.of(Movie("Arrival", null, null, null, emptyList()))
        assertThrows<VersionConflictException> { service.delete(id, 2) }
        verify(exactly = 0) { repository.delete(any()) }

        every { repository.findById(id) } returns Optional.empty()
        assertThrows<NotFoundException> { service.delete(id, 1) }
    }

    @Test
    fun `setting artwork returns the artwork it replaced, read from the same row`() {
        val previous = UUID.randomUUID()
        val next = UUID.randomUUID()
        every { repository.findById(id) } returns Optional.of(Movie("Arrival", null, null, null, emptyList()).apply { artworkId = previous })

        val change = service.setArtwork(id, 1, next)

        assertThat(change.movie.artworkId).isEqualTo(next)
        assertThat(change.previousArtworkId).isEqualTo(previous)
    }

    @Test
    fun `clearing artwork returns what was removed, and nothing when there was none`() {
        val previous = UUID.randomUUID()
        every { repository.findById(id) } returns Optional.of(Movie("Arrival", null, null, null, emptyList()).apply { artworkId = previous })
        val change = service.clearArtwork(id, 1)
        assertThat(change.movie.artworkId).isNull()
        assertThat(change.previousArtworkId).isEqualTo(previous)

        every { repository.findById(id) } returns Optional.of(Movie("Arrival", null, null, null, emptyList()))
        assertThat(service.clearArtwork(id, 1).previousArtworkId).isNull()
    }

    @Test
    fun `artwork changes from a stale version are conflicts and change nothing`() {
        val previous = UUID.randomUUID()
        val stored = Movie("Arrival", null, null, null, emptyList()).apply { artworkId = previous }
        every { repository.findById(id) } returns Optional.of(stored)

        assertThrows<VersionConflictException> { service.setArtwork(id, 2, UUID.randomUUID()) }
        assertThrows<VersionConflictException> { service.clearArtwork(id, 2) }

        assertThat(stored.artworkId).isEqualTo(previous)
        verify(exactly = 0) { repository.saveAndFlush(any()) }
    }
}
