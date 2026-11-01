package com.moviecatalog.catalog.movie

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
import java.util.UUID

@DataJpaTest
@Import(TestcontainersConfiguration::class)
class MovieRepositoryTest(
    @Autowired private val repository: MovieRepository,
    @Autowired private val entityManager: TestEntityManager,
    @Autowired private val jdbc: JdbcClient,
) {
    @Test
    fun `a saved movie reads back with every field, an id and version 1`() {
        val saved = repository.saveAndFlush(Movie("Amélie", "Paris, 2001.", LocalDate.of(2001, 4, 25), 122, listOf("Comédie", "Romance")))
        entityManager.clear()

        val read = repository.findById(saved.id!!).orElseThrow()

        assertThat(read.title).isEqualTo("Amélie")
        assertThat(read.synopsis).isEqualTo("Paris, 2001.")
        assertThat(read.releaseDate).isEqualTo(LocalDate.of(2001, 4, 25))
        assertThat(read.runtimeMinutes).isEqualTo(122)
        assertThat(read.genres).containsExactly("Comédie", "Romance")
        assertThat(read.artworkId).isNull()
        assertThat(read.version).isEqualTo(1)
        assertThat(read.createdAt).isEqualTo(saved.createdAt)
        assertThat(read.updatedAt).isEqualTo(read.createdAt)
    }

    @Test
    fun `a marked update raises the version by one and moves updatedAt, but not createdAt`() {
        val saved = repository.saveAndFlush(Movie("Arrival", null, null, null, emptyList()))
        val createdAt = saved.createdAt

        saved.title = "Arrival (2016)"
        saved.markUpdated()
        repository.saveAndFlush(saved)
        entityManager.clear()

        val read = repository.findById(saved.id!!).orElseThrow()
        assertThat(read.version).isEqualTo(2)
        assertThat(read.createdAt).isEqualTo(createdAt)
        assertThat(read.updatedAt).isAfter(createdAt)
    }

    @Test
    fun `a marked update with identical values still raises the version`() {
        val saved = repository.saveAndFlush(Movie("Arrival", null, null, null, emptyList()))

        saved.markUpdated()
        repository.saveAndFlush(saved)
        entityManager.clear()

        assertThat(repository.findById(saved.id!!).orElseThrow().version).isEqualTo(2)
    }

    @Test
    fun `a write based on an old read fails instead of overwriting the newer row`() {
        val saved = repository.saveAndFlush(Movie("Arrival", null, null, null, emptyList()))
        entityManager.detach(saved)
        // Someone else saves a change.
        jdbc.sql("UPDATE movie SET title = 'Theirs', version = version + 1 WHERE id = ?").param(saved.id).update()

        saved.title = "Mine"
        assertThrows<OptimisticLockingFailureException> { repository.saveAndFlush(saved) }
    }

    @Test
    fun `the database rejects an over-long title, even if the service were bypassed`() {
        assertThrows<DataIntegrityViolationException> { repository.saveAndFlush(Movie("x".repeat(201), null, null, null, emptyList())) }
    }

    @Test
    fun `the database rejects a runtime of 0, even if the service were bypassed`() {
        assertThrows<DataIntegrityViolationException> { repository.saveAndFlush(Movie("Arrival", null, null, 0, emptyList())) }
    }

    @Test
    fun `pages newest first, with the id breaking ties, from any offset`() {
        val ids = (1..3).map { repository.save(Movie("Movie $it", null, null, null, emptyList())).id!! }
        repository.flush()
        // Same creation time, so only the id decides the order.
        jdbc.sql("UPDATE movie SET created_at = '2026-01-01T00:00:00Z'").update()
        entityManager.clear()
        val newestFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))

        val page = repository.findAll(OffsetPageRequest(1, 5, newestFirst))

        assertThat(page.content.map { it.id }).containsExactlyElementsOf(ids.sortedByDescending { it.toString() }.drop(1))
        assertThat(page.totalElements).isEqualTo(3)
    }

    @Test
    fun `an unknown id finds nothing`() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty()
    }
}
