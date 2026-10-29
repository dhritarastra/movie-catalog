package com.moviecatalog.catalog.credit

import com.moviecatalog.catalog.common.AlreadyExistsException
import com.moviecatalog.catalog.common.InvalidArgumentException
import com.moviecatalog.catalog.common.NotFoundException
import com.moviecatalog.catalog.common.validation.inputValidatorOn
import com.moviecatalog.catalog.movie.MovieRepository
import com.moviecatalog.catalog.person.PersonRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import java.sql.SQLException
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class CreditServiceTest {
    private val credits = mockk<CreditRepository>()
    private val movies = mockk<MovieRepository>()
    private val people = mockk<PersonRepository>()
    private val service = CreditService(credits, movies, people, inputValidatorOn(LocalDate.of(2026, 10, 28)))

    private val movie = UUID.randomUUID()
    private val person = UUID.randomUUID()
    private val cast = CreditInput(CreditType.CAST, "Louise Banks", 1)

    init {
        every { movies.existsById(movie) } returns true
        every { people.existsById(person) } returns true
        every { credits.saveAndFlush(any()) } answers { firstArg() }
    }

    @Test
    fun `add stores the credit for that movie and person`() {
        val credit = service.add(movie, person, cast)

        assertThat(credit.movieId).isEqualTo(movie)
        assertThat(credit.personId).isEqualTo(person)
        assertThat(credit.type).isEqualTo(CreditType.CAST)
        assertThat(credit.characterName).isEqualTo("Louise Banks")
        assertThat(credit.billingOrder).isEqualTo(1)
    }

    @Test
    fun `invalid input is rejected before anything is looked up or stored`() {
        assertThrows<InvalidArgumentException> { service.add(movie, person, CreditInput(CreditType.DIRECTOR, "Louise Banks", 0)) }

        verify(exactly = 0) { movies.existsById(any()) }
        verify(exactly = 0) { credits.saveAndFlush(any()) }
    }

    @Test
    fun `an unknown movie or person is not found, and nothing is stored`() {
        val unknown = UUID.randomUUID()
        every { movies.existsById(unknown) } returns false
        every { people.existsById(unknown) } returns false

        assertThat(assertThrows<NotFoundException> { service.add(unknown, person, cast) }.message).contains("Movie")
        assertThat(assertThrows<NotFoundException> { service.add(movie, unknown, cast) }.message).contains("Person")
        verify(exactly = 0) { credits.saveAndFlush(any()) }
    }

    @Test
    fun `a duplicate credit is a conflict`() {
        every { credits.saveAndFlush(any()) } throws violation("credit_unique")

        assertThrows<AlreadyExistsException> { service.add(movie, person, cast) }
    }

    @Test
    fun `a movie or person deleted after the check is not found`() {
        every { credits.saveAndFlush(any()) } throws violation("credit_movie_id_fkey")

        assertThrows<NotFoundException> { service.add(movie, person, cast) }
    }

    @Test
    fun `any other database failure is not disguised`() {
        every { credits.saveAndFlush(any()) } throws violation("credit_billing_order_check")

        assertThrows<DataIntegrityViolationException> { service.add(movie, person, cast) }
    }

    @Test
    fun `an update replaces the role, character and billing order, clearing an omitted character`() {
        val id = UUID.randomUUID()
        val stored = Credit(movie, person, CreditType.CAST, "Louise Banks", 1)
        every { credits.findById(id) } returns Optional.of(stored)

        val updated = service.update(id, CreditInput(CreditType.PRODUCER, null, 4))

        assertThat(updated.type).isEqualTo(CreditType.PRODUCER)
        assertThat(updated.characterName).isNull()
        assertThat(updated.billingOrder).isEqualTo(4)
        assertThat(updated.movieId).isEqualTo(movie)
        verify { credits.saveAndFlush(stored) }
    }

    @Test
    fun `updating an unknown credit is not found, and an invalid update looks nothing up`() {
        val id = UUID.randomUUID()
        assertThrows<InvalidArgumentException> { service.update(id, CreditInput(null, null, 0)) }
        verify(exactly = 0) { credits.findById(any()) }

        every { credits.findById(id) } returns Optional.empty()
        assertThrows<NotFoundException> { service.update(id, cast) }
    }

    @Test
    fun `removing a credit that is not there is not found`() {
        val id = UUID.randomUUID()
        every { credits.removeById(id) } returnsMany listOf(1, 0)

        service.remove(id)
        assertThrows<NotFoundException> { service.remove(id) }
    }

    @Test
    fun `lookups group credits by movie and by person, in billing order`() {
        val other = UUID.randomUUID()
        val sort = slot<Sort>()
        val first = Credit(movie, person, CreditType.CAST, null, 0)
        val second = Credit(movie, other, CreditType.DIRECTOR, null, 1)
        every { credits.findAllByMovieIdIn(listOf(movie), capture(sort)) } returns listOf(first, second)
        every { credits.findAllByPersonIdIn(listOf(person, other), any()) } returns listOf(first, second)

        assertThat(service.byMovieIds(listOf(movie))).isEqualTo(mapOf(movie to listOf(first, second)))
        assertThat(service.byPersonIds(listOf(person, other))).isEqualTo(mapOf(person to listOf(first), other to listOf(second)))
        assertThat(sort.captured).isEqualTo(Sort.by("billingOrder", "createdAt", "id"))
    }

    private fun violation(constraint: String) =
        DataIntegrityViolationException("rejected", ConstraintViolationException("rejected", SQLException("rejected"), constraint))
}
