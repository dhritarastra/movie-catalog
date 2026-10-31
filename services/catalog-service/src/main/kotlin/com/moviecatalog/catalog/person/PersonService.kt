package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.FailedPreconditionException
import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import com.moviecatalog.catalog.common.NotFoundException
import com.moviecatalog.catalog.common.Paging
import com.moviecatalog.catalog.common.VersionConflictException
import com.moviecatalog.catalog.common.validation.InputValidator
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional(readOnly = true)
class PersonService(
    private val repository: PersonRepository,
    private val validator: InputValidator,
    private val paging: Paging,
) {
    @Transactional
    fun create(input: PersonInput): Person {
        validator.check(input)
        return repository.save(Person(input.name, input.bio, input.birthDate?.let(LocalDate::parse)))
    }

    fun get(id: UUID): Person = repository.findByIdOrNull(id) ?: throw notFound(id)

    fun getAll(ids: Collection<UUID>): List<Person> = repository.findAllById(ids)

    /** Sorted by name (ICU collation), then id so pages stay stable. */
    fun list(offset: Int, limit: Int, nameContains: String = ""): Page<Person> {
        val name = nameContains.trim()
        // Longer than any name, so nothing can match.
        if (name.length > PersonInput.NAME_MAX) {
            throw InvalidArgumentException(listOf(FieldViolation("name_contains", "must be at most ${PersonInput.NAME_MAX} characters")))
        }
        val page = paging.of(offset, limit, BY_NAME)
        return if (name.isEmpty()) repository.findAll(page) else repository.findAllByNameContainingIgnoreCase(name, page)
    }

    @Transactional
    fun update(id: UUID, expectedVersion: Int, input: PersonInput): Person {
        validator.check(input)
        val person = current(id, expectedVersion)
        person.name = input.name
        person.bio = input.bio
        person.birthDate = input.birthDate?.let(LocalDate::parse)
        person.markUpdated()
        return repository.saveAndFlush(person)
    }

    /** The credit foreign key blocks this while the person has credits, including new ones. */
    @Transactional
    fun delete(id: UUID, expectedVersion: Int) {
        repository.delete(current(id, expectedVersion))
        try {
            repository.flush()
        } catch (e: DataIntegrityViolationException) {
            // Only the credit foreign key means the person is still credited.
            if ((e.cause as? ConstraintViolationException)?.constraintName != "credit_person_id_fkey") throw e
            throw FailedPreconditionException("This person is credited on a movie. Remove their credits first, then delete them.")
        }
    }

    private fun current(id: UUID, expectedVersion: Int): Person {
        val person = repository.findByIdOrNull(id) ?: throw notFound(id)
        if (person.version != expectedVersion) {
            throw VersionConflictException("Person $id is at version ${person.version}, not $expectedVersion; reload and try again")
        }
        return person
    }

    private fun notFound(id: UUID) = NotFoundException("Person $id not found")

    private companion object {
        val BY_NAME: Sort = Sort.by("name", "id")
    }
}
