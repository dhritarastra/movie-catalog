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
    fun `a marked update raises the version by one and moves updatedAt`() {
        val saved = repository.saveAndFlush(Person("Amy Adams", null, null))
        val createdAt = saved.createdAt

        saved.bio = "Actor."
        saved.markUpdated()
        repository.saveAndFlush(saved)
        entityManager.clear()

        val read = repository.findById(saved.id!!).orElseThrow()
        assertThat(read.version).isEqualTo(2)
        assertThat(read.updatedAt).isAfter(createdAt)
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
        assertThat(page.content.take(2).map { it.id.toString() }).isSorted()
    }

    @Test
    fun `finds names containing the text, ignoring case, in name order`() {
        listOf("Amy Adams", "Jeremy Renner", "amy poehler", "Tamy Lee").forEach { repository.save(Person(it, null, null)) }
        repository.flush()

        val found = repository.findAllByNameContainingIgnoreCase("AMY", OffsetPageRequest(0, 10, Sort.by("name", "id")))

        assertThat(found.content.map { it.name }).containsExactly("Amy Adams", "amy poehler", "Tamy Lee")
        assertThat(found.totalElements).isEqualTo(3)
    }

    @Test
    fun `percent and underscore in the text are matched literally, not as wildcards`() {
        listOf("100% Real", "1000 Real", "A_B", "AxB").forEach { repository.save(Person(it, null, null)) }
        repository.flush()
        val byName = Sort.by("name", "id")

        assertThat(repository.findAllByNameContainingIgnoreCase("0%", OffsetPageRequest(0, 10, byName)).content.map { it.name })
            .containsExactly("100% Real")
        assertThat(repository.findAllByNameContainingIgnoreCase("_", OffsetPageRequest(0, 10, byName)).content.map { it.name })
            .containsExactly("A_B")
    }

    @Test
    fun `ignoring case covers non-ASCII letters, but accents still count`() {
        listOf("Zoë Ünal", "Zoe Unal").forEach { repository.save(Person(it, null, null)) }
        repository.flush()
        val byName = Sort.by("name", "id")

        assertThat(repository.findAllByNameContainingIgnoreCase("ZOË", OffsetPageRequest(0, 10, byName)).content.map { it.name })
            .containsExactly("Zoë Ünal")
        assertThat(repository.findAllByNameContainingIgnoreCase("ë ü", OffsetPageRequest(0, 10, byName)).content.map { it.name })
            .containsExactly("Zoë Ünal")
        assertThat(repository.findAllByNameContainingIgnoreCase("zoe", OffsetPageRequest(0, 10, byName)).content.map { it.name })
            .containsExactly("Zoe Unal")
    }

    @Test
    fun `a backslash in the text is matched literally`() {
        listOf("""AC\DC""", "ACDC").forEach { repository.save(Person(it, null, null)) }
        repository.flush()

        assertThat(repository.findAllByNameContainingIgnoreCase("""\""", OffsetPageRequest(0, 10, Sort.by("name"))).content.map { it.name })
            .containsExactly("""AC\DC""")
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
