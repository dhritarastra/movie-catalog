package com.moviecatalog.media.artwork

import com.moviecatalog.media.TestcontainersConfiguration
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/** The entity mapping against the real schema: Flyway's migration, the content-type column and constraints. */
@DataJpaTest
@Import(TestcontainersConfiguration::class)
class ArtworkRepositoryTest(
    @Autowired private val repository: ArtworkRepository,
    @Autowired private val entityManager: TestEntityManager,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val dataSource: DataSource,
    @Autowired private val entityManagerFactory: EntityManagerFactory,
    @Autowired private val transactions: PlatformTransactionManager,
) {
    @Test
    fun `Hibernate checks the Artwork mapping against the Flyway schema at startup`() {
        assertThat(entityManagerFactory.properties["hibernate.hbm2ddl.auto"]).isEqualTo("validate")
    }

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
        // Committed from another connection, so that connection can see the row below.
        dataSource.connection.use {
            it.prepareStatement("INSERT INTO artwork (id, content_type, size_bytes, width, height, sha256) VALUES (?, 'image/png', 1, 1, 1, '00')")
                .apply { setObject(1, id) }.executeUpdate()
        }

        assertThat(repository.removeById(id)).isEqualTo(1)
        assertThat(repository.removeById(id)).isZero()
        assertThat(repository.findById(id)).isEmpty()
    }

    /**
     * Two requests deleting the same artwork at once. This test runs outside the test transaction
     * (NOT_SUPPORTED), so each delete commits for real; it creates its own row and removes it at
     * the end, because nothing is rolled back for it.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `a second delete running at the same time waits for the first, then reports that nothing existed`() {
        val id = repository.saveAndFlush(Artwork(UUID.randomUUID(), ImageFormat.PNG, 1, 1, 1, "00")).id
        val other = Executors.newSingleThreadExecutor()
        try {
            val transaction = TransactionTemplate(transactions)
            var second: Future<Int>? = null
            val first = transaction.execute {
                val removed = repository.removeById(id)
                // The second delete starts while this transaction still holds the row.
                second = other.submit<Int> { transaction.execute { repository.removeById(id) } }
                awaitBlockedDelete()
                removed
            }

            assertThat(first).isEqualTo(1)
            assertThat(second!!.get(10, TimeUnit.SECONDS)).isZero()
            assertThat(repository.existsById(id)).isFalse()
        } finally {
            // The worker must have let go of its connection before the cleanup below.
            other.shutdownNow()
            other.awaitTermination(10, TimeUnit.SECONDS)
            dataSource.connection.use {
                it.prepareStatement("DELETE FROM artwork WHERE id = ?").apply { setObject(1, id) }.executeUpdate()
            }
        }
    }

    /**
     * Waits until Postgres reports a delete from artwork in this test's database waiting for
     * another transaction to finish, which is how a delete waits for a row another one holds.
     */
    private fun awaitBlockedDelete() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val waiting = dataSource.connection.use {
                val rows = it.prepareStatement(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() " +
                        "AND wait_event_type = 'Lock' AND wait_event = 'transactionid' AND query ILIKE 'delete from artwork%'",
                ).executeQuery()
                rows.next()
                rows.getInt(1)
            }
            if (waiting > 0) return
            Thread.sleep(50)
        }
        throw AssertionError("The second delete never waited for the first")
    }

    @Test
    fun `the database rejects an empty file, even if the service were bypassed`() {
        assertThrows<DataIntegrityViolationException> {
            repository.saveAndFlush(Artwork(UUID.randomUUID(), ImageFormat.PNG, 0, 1, 1, "00"))
        }
    }
}
