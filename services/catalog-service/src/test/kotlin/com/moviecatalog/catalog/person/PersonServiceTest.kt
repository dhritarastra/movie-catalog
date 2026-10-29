package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.FailedPreconditionException
import com.moviecatalog.catalog.common.FieldViolation
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
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.sql.SQLException
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class PersonServiceTest {
    private val repository = mockk<PersonRepository>(relaxUnitFun = true)
    private val service = PersonService(repository, inputValidatorOn(LocalDate.of(2026, 10, 28)), Paging(PagingProperties(maxLimit = 100)))

    private val id = UUID.randomUUID()
    private val input = PersonInput("Amy Adams", "Actor.", "1974-08-20")

    init {
        every { repository.save(any()) } answers { firstArg() }
        every { repository.saveAndFlush(any()) } answers { firstArg() }
    }

    @Test
    fun `create stores the input with the date parsed`() {
        val created = service.create(input)

        assertThat(created.name).isEqualTo("Amy Adams")
        assertThat(created.birthDate).isEqualTo(LocalDate.of(1974, 8, 20))
        verify { repository.save(created) }
    }

    @Test
    fun `a birth date in the future is rejected before anything is stored`() {
        assertThrows<InvalidArgumentException> { service.create(input.copy(birthDate = "2026-10-29")) }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `get reports an unknown person as not found`() {
        every { repository.findById(id) } returns Optional.empty()

        assertThrows<NotFoundException> { service.get(id) }
    }

    @Test
    fun `list asks for alphabetical order, with the id as tie-breaker`() {
        val pageable = slot<Pageable>()
        every { repository.findAll(capture(pageable)) } returns PageImpl(emptyList())

        service.list(0, 25)

        assertThat(pageable.captured.sort).isEqualTo(Sort.by("name", "id"))
        assertThat(pageable.captured.pageSize).isEqualTo(25)
    }

    @Test
    fun `a name filter is trimmed and narrows the list, and a blank one lists everyone`() {
        every { repository.findAllByNameContainingIgnoreCase("Amy", any()) } returns PageImpl(emptyList())
        every { repository.findAll(any<Pageable>()) } returns PageImpl(emptyList())

        service.list(0, 20, "  Amy ")
        service.list(0, 20, "   ")

        verify(exactly = 1) { repository.findAllByNameContainingIgnoreCase("Amy", any()) }
        verify(exactly = 1) { repository.findAll(any<Pageable>()) }
    }

    @Test
    fun `a name filter of 200 characters is accepted, and one longer is rejected before any query`() {
        every { repository.findAllByNameContainingIgnoreCase(any(), any()) } returns PageImpl(emptyList())
        service.list(0, 20, "x".repeat(200))

        val error = assertThrows<InvalidArgumentException> { service.list(0, 20, "x".repeat(201)) }

        assertThat(error.violations).containsExactly(FieldViolation("name_contains", "must be at most 200 characters"))
        verify(exactly = 1) { repository.findAllByNameContainingIgnoreCase(any(), any()) }
    }

    @Test
    fun `an update replaces every field, clearing omitted ones`() {
        val stored = Person("Old", "Old bio", LocalDate.of(1970, 1, 1))
        every { repository.findById(id) } returns Optional.of(stored)

        val updated = service.update(id, 1, PersonInput("New", null, null))

        assertThat(updated.name).isEqualTo("New")
        assertThat(updated.bio).isNull()
        assertThat(updated.birthDate).isNull()
        assertThat(updated.updatedAt).isAfter(updated.createdAt)
        verify { repository.saveAndFlush(stored) }
    }

    @Test
    fun `saving identical values still marks the person updated, so their version will advance`() {
        val stored = Person("Amy Adams", null, null)
        every { repository.findById(id) } returns Optional.of(stored)

        val updated = service.update(id, 1, PersonInput("Amy Adams", null, null))

        assertThat(updated.updatedAt).isAfter(updated.createdAt)
    }

    @Test
    fun `an update from a stale version is a conflict and changes nothing`() {
        val stored = Person("Theirs", null, null)
        every { repository.findById(id) } returns Optional.of(stored)

        assertThrows<VersionConflictException> { service.update(id, 2, input) }

        assertThat(stored.name).isEqualTo("Theirs")
        verify(exactly = 0) { repository.saveAndFlush(any()) }
    }

    @Test
    fun `delete removes the person if the version matches`() {
        val stored = Person("Amy Adams", null, null)
        every { repository.findById(id) } returns Optional.of(stored)

        service.delete(id, 1)

        verify { repository.delete(stored) }
    }

    @Test
    fun `deleting a person who still has credits is refused with what to do first`() {
        every { repository.findById(id) } returns Optional.of(Person("Amy Adams", null, null))
        every { repository.flush() } throws violation("credit_person_id_fkey")

        val refused = assertThrows<FailedPreconditionException> { service.delete(id, 1) }

        assertThat(refused.message).contains("Remove their credits first")
    }

    @Test
    fun `any other database failure on delete is not disguised as credits`() {
        every { repository.findById(id) } returns Optional.of(Person("Amy Adams", null, null))
        every { repository.flush() } throws violation("some_other_constraint")

        assertThrows<DataIntegrityViolationException> { service.delete(id, 1) }
    }

    private fun violation(constraint: String) =
        DataIntegrityViolationException("rejected", ConstraintViolationException("rejected", SQLException("rejected"), constraint))

    @Test
    fun `delete from a stale version keeps the person, and an unknown person is not found`() {
        every { repository.findById(id) } returns Optional.of(Person("Amy Adams", null, null))
        assertThrows<VersionConflictException> { service.delete(id, 2) }
        verify(exactly = 0) { repository.delete(any()) }

        every { repository.findById(id) } returns Optional.empty()
        assertThrows<NotFoundException> { service.delete(id, 1) }
    }
}
