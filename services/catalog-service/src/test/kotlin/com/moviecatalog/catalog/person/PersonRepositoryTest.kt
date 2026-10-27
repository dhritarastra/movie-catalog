package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.TestcontainersConfiguration
import com.moviecatalog.catalog.common.OffsetPageRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.LocalDate

/** The entity mapping against the real schema: Flyway's migration, the ICU collation and constraints. */
@DataJpaTest
@Import(TestcontainersConfiguration::class)
class PersonRepositoryTest(
    @Autowired private val repository: PersonRepository,
    @Autowired private val entityManager: TestEntityManager,
    @Autowired private val jdbc: JdbcClient,
) {
    @Test
    fun `a saved person reads back with every field, an id and version 1`() {
        val saved = repository.saveAndFlush(Person("Denis Villeneuve", "Québécois director.", LocalDate.of(1967, 10, 3)))
        entityManager.clear()

        val read = repository.findById(saved.id!!).orElseThrow()

        assertThat(read.name).isEqualTo("Denis Villeneuve")
        assertThat(read.bio).isEqualTo("Québécois director.")
        assertThat(read.birthDate).isEqualTo(LocalDate.of(1967, 10, 3))
        assertThat(read.version).isEqualTo(1)
        assertThat(read.updatedAt).isEqualTo(read.createdAt)
    }

    @Test
    fun `an update raises the version by one`() {
        val saved = repository.saveAndFlush(Person("Amy Adams", null, null))

        saved.bio = "Actor."
        repository.saveAndFlush(saved)
        entityManager.clear()

        assertThat(repository.findById(saved.id!!).orElseThrow().version).isEqualTo(2)
    }

    @Test
    fun `a write based on an old read fails instead of overwriting the newer row`() {
        val saved = repository.saveAndFlush(Person("Amy Adams", null, null))
        entityManager.detach(saved)
        jdbc.sql("UPDATE person SET name = 'Theirs', version = version + 1 WHERE id = ?").param(saved.id).update()

        saved.name = "Mine"
        assertThrows<OptimisticLockingFailureException> { repository.saveAndFlush(saved) }
    }

    @Test
    fun `the database rejects a birth date before 1800`() {
        assertThrows<DataIntegrityViolationException> { repository.saveAndFlush(Person("Old", null, LocalDate.of(1799, 12, 31))) }
    }

    @Test
    fun `sorts names alphabetically with ICU, with the id breaking identical-name ties`() {
        listOf("Jeremy Renner", "denis Villeneuve", "Émile Hirsch", "Amy Adams", "Amy Adams").forEach { repository.save(Person(it, null, null)) }
        repository.flush()
        entityManager.clear()

        val page = repository.findAll(OffsetPageRequest(0, 10, Sort.by("name", "id")))

        assertThat(page.content.map { it.name }).containsExactly("Amy Adams", "Amy Adams", "denis Villeneuve", "Émile Hirsch", "Jeremy Renner")
        // Postgres orders uuids as their canonical strings sort.
        assertThat(page.content.take(2).map { it.id.toString() }).isSorted()
    }

    @Test
    fun `case and accent distinctions precede the id tie breaker`() {
        jdbc.sql(
            """
            INSERT INTO person (id, name) VALUES
            ('00000000-0000-0000-0000-000000000001', 'Amy'),
            ('00000000-0000-0000-0000-000000000002', 'amy'),
            ('00000000-0000-0000-0000-000000000003', 'Émile'),
            ('00000000-0000-0000-0000-000000000004', 'Emile')
            """,
        ).update()

        val page = repository.findAll(OffsetPageRequest(0, 10, Sort.by("name", "id")))

        assertThat(page.content.map { it.name }).containsExactly("amy", "Amy", "Emile", "Émile")
    }
}
