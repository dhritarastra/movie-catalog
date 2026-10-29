package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.FailedPreconditionException
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

/** People follow the movie rules: full-replacement updates and deletes guarded by the version. */
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

    /** The people who exist among [ids]; unknown IDs are left out. */
    fun getAll(ids: Collection<UUID>): List<Person> = repository.findAllById(ids)

    /**
     * Alphabetical through the name column's ICU collation (case and accents break letter ties)
     * (V2__create_person.sql). The id tie-breaker keeps people with the same name in a stable order.
     */
    fun list(offset: Int, limit: Int): Page<Person> = repository.findAll(paging.of(offset, limit, BY_NAME))

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

    /**
     * Deletes the person if they are still at [expectedVersion] and have no credits. The
     * database's foreign key from credit to person refuses the delete while any credit exists,
     * so a credit added at the same moment is caught too.
     */
    @Transactional
    fun delete(id: UUID, expectedVersion: Int) {
        repository.delete(current(id, expectedVersion))
        try {
            repository.flush()
        } catch (e: DataIntegrityViolationException) {
            // Only the credit foreign key means "still credited"; anything else is reported as it is.
            if ((e.cause as? ConstraintViolationException)?.constraintName != "credit_person_id_fkey") throw e
            throw FailedPreconditionException("This person is credited on a movie. Remove their credits first, then delete them.")
        }
    }

    /** The person the caller wants to change, if they saw the current version; see MovieService. */
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
