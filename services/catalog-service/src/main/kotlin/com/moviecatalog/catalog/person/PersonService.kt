package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.CatalogException
import com.moviecatalog.catalog.common.NotFoundException
import com.moviecatalog.catalog.common.Page
import com.moviecatalog.catalog.common.VersionConflictException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** People follow the movie rules: full-replacement updates and deletes guarded by the version (DECISIONS D5). */
@Service
class PersonService(private val repository: PersonRepository) {
    fun create(input: PersonInput): Person = repository.insert(PersonValidator.validate(input))

    fun get(id: UUID): Person = repository.findById(id) ?: throw notFound(id)

    @Transactional(readOnly = true)
    fun list(page: Page): PersonPage = PersonPage(repository.findPage(page), repository.count())

    @Transactional
    fun update(id: UUID, expectedVersion: Int, input: PersonInput): Person {
        val draft = PersonValidator.validate(input)
        return repository.update(id, expectedVersion, draft) ?: throw missingOrStale(id, expectedVersion)
    }

    @Transactional
    fun delete(id: UUID, expectedVersion: Int) {
        if (!repository.delete(id, expectedVersion)) throw missingOrStale(id, expectedVersion)
    }

    /** After a versioned write matched no row: either the person is gone or someone changed them first. */
    private fun missingOrStale(id: UUID, expectedVersion: Int): CatalogException {
        val currentVersion = repository.findVersion(id) ?: return notFound(id)
        return VersionConflictException("Person $id is at version $currentVersion, not $expectedVersion; reload and try again")
    }

    private fun notFound(id: UUID) = NotFoundException("Person $id not found")
}
