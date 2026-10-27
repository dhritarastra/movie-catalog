package com.moviecatalog.media.artwork

import com.moviecatalog.media.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

/** The entity mapping against the real schema: Flyway's migration, the content-type column and constraints. */
@DataJpaTest
@Import(TestcontainersConfiguration::class)
class ArtworkRepositoryTest(
    @Autowired private val repository: ArtworkRepository,
    @Autowired private val entityManager: TestEntityManager,
    @Autowired private val jdbc: JdbcClient,
) {
    @Test
    fun `saved artwork reads back with every field, its format stored as the content type`() {
        val id = UUID.randomUUID()
        val saved = repository.saveAndFlush(Artwork(id, ImageFormat.JPEG, 1234, 640, 480, "ab".repeat(32)))
        entityManager.clear()

        val read = repository.findById(id).orElseThrow()

        assertThat(read.format).isEqualTo(ImageFormat.JPEG)
        assertThat(read.sizeBytes).isEqualTo(1234)
        assertThat(read.width to read.height).isEqualTo(640 to 480)
        assertThat(read.sha256).isEqualTo("ab".repeat(32))
        assertThat(read.createdAt).isEqualTo(saved.createdAt)
        assertThat(jdbc.sql("SELECT content_type FROM artwork WHERE id = ?").param(id).query(String::class.java).single())
            .isEqualTo("image/jpeg")
    }

    @Test
    fun `removing reports one row the first time and none the second`() {
        val id = UUID.randomUUID()
        repository.saveAndFlush(Artwork(id, ImageFormat.PNG, 1, 1, 1, "00"))

        assertThat(repository.removeById(id)).isEqualTo(1)
        assertThat(repository.removeById(id)).isZero()
        assertThat(repository.findById(id)).isEmpty()
    }

    @Test
    fun `the database rejects an empty file, even if the service were bypassed`() {
        assertThrows<DataIntegrityViolationException> {
            repository.saveAndFlush(Artwork(UUID.randomUUID(), ImageFormat.PNG, 0, 1, 1, "00"))
        }
    }
}
