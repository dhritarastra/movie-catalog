package com.moviecatalog.catalog.person

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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
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
    fun `an update replaces every field, clearing omitted ones`() {
        val stored = Person("Old", "Old bio", LocalDate.of(1970, 1, 1))
        every { repository.findById(id) } returns Optional.of(stored)

        val updated = service.update(id, 1, PersonInput("New", null, null))

        assertThat(updated.name).isEqualTo("New")
        assertThat(updated.bio).isNull()
        assertThat(updated.birthDate).isNull()
        verify { repository.saveAndFlush(stored) }
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
    fun `delete from a stale version keeps the person, and an unknown person is not found`() {
        every { repository.findById(id) } returns Optional.of(Person("Amy Adams", null, null))
        assertThrows<VersionConflictException> { service.delete(id, 2) }
        verify(exactly = 0) { repository.delete(any()) }

        every { repository.findById(id) } returns Optional.empty()
        assertThrows<NotFoundException> { service.delete(id, 1) }
    }
}
