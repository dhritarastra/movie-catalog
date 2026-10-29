package com.moviecatalog.catalog.search

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import com.moviecatalog.catalog.config.SearchProperties
import com.moviecatalog.catalog.movie.Movie
import com.moviecatalog.catalog.movie.MovieRepository
import com.moviecatalog.catalog.person.Person
import com.moviecatalog.catalog.person.PersonRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class SearchServiceTest {
    private val search = mockk<SearchRepository>()
    private val movies = mockk<MovieRepository>()
    private val people = mockk<PersonRepository>()
    private val service = SearchService(search, movies, people, SearchProperties(maxLimit = 50))

    @Test
    fun `the query is trimmed, and hits keep the ranked order with movies and people mixed`() {
        val (arrivalId, arrival) = movie()
        val (amyId, amy) = person()
        every { search.search("arr", 10) } returns listOf(SearchMatch(SearchKind.PERSON, amyId), SearchMatch(SearchKind.MOVIE, arrivalId))
        every { movies.findAllById(listOf(arrivalId)) } returns listOf(arrival)
        every { people.findAllById(listOf(amyId)) } returns listOf(amy)

        val hits = service.search("  arr ", 10)

        assertThat(hits).containsExactly(SearchHit.OfPerson(amy), SearchHit.OfMovie(arrival))
    }

    @Test
    fun `a record deleted between the two queries is left out`() {
        val gone = UUID.randomUUID()
        every { search.search("x", 10) } returns listOf(SearchMatch(SearchKind.MOVIE, gone))
        every { movies.findAllById(listOf(gone)) } returns emptyList()
        every { people.findAllById(emptyList()) } returns emptyList()

        assertThat(service.search("x", 10)).isEmpty()
    }

    @Test
    fun `a blank or whitespace-only query is rejected before any query`() {
        listOf("", "  \t ").forEach {
            val error = assertThrows<InvalidArgumentException> { service.search(it, 10) }
            assertThat(error.violations).describedAs("'$it'").containsExactly(FieldViolation("query", "is required"))
        }
        verify(exactly = 0) { search.search(any(), any()) }
    }

    @Test
    fun `accepts a 200-character query and rejects 201`() {
        every { search.search(any(), any()) } returns emptyList()
        every { movies.findAllById(any()) } returns emptyList()
        every { people.findAllById(any()) } returns emptyList()
        service.search("x".repeat(200), 10)
        // Non-ASCII letters count one each, as they do for titles and names.
        service.search("é".repeat(200), 10)

        val error = assertThrows<InvalidArgumentException> { service.search("x".repeat(201), 10) }
        assertThrows<InvalidArgumentException> { service.search("é".repeat(201), 10) }

        assertThat(error.violations).containsExactly(FieldViolation("query", "must be at most 200 characters"))
    }

    @Test
    fun `accepts limits from 1 to the configured maximum and rejects others, never cutting them down`() {
        every { search.search(any(), any()) } returns emptyList()
        every { movies.findAllById(any()) } returns emptyList()
        every { people.findAllById(any()) } returns emptyList()
        service.search("x", 1)
        service.search("x", 50)

        listOf(0, -1, 51).forEach {
            val error = assertThrows<InvalidArgumentException> { service.search("x", it) }
            assertThat(error.violations).describedAs("$it").containsExactly(FieldViolation("limit", "must be between 1 and 50"))
        }
    }

    @Test
    fun `reports a bad query and a bad limit together`() {
        val error = assertThrows<InvalidArgumentException> { service.search(" ", 0) }

        assertThat(error.violations.map { it.field }).containsExactly("query", "limit")
    }

    // Saved entities get their ID from the database, so these stand-ins provide one.
    private fun movie(): Pair<UUID, Movie> {
        val id = UUID.randomUUID()
        return id to mockk<Movie> { every { this@mockk.id } returns id }
    }

    private fun person(): Pair<UUID, Person> {
        val id = UUID.randomUUID()
        return id to mockk<Person> { every { this@mockk.id } returns id }
    }
}
