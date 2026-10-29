package com.moviecatalog.catalog.credit

import com.moviecatalog.catalog.TestcontainersConfiguration
import com.moviecatalog.catalog.movie.Movie
import com.moviecatalog.catalog.movie.MovieRepository
import com.moviecatalog.catalog.person.Person
import com.moviecatalog.catalog.person.PersonRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

/** The credit mapping against the real schema: the unique rule, the role check and both foreign keys. */
@DataJpaTest
@Import(TestcontainersConfiguration::class)
class CreditRepositoryTest(
    @Autowired private val credits: CreditRepository,
    @Autowired private val movies: MovieRepository,
    @Autowired private val people: PersonRepository,
    @Autowired private val entityManager: TestEntityManager,
    @Autowired private val jdbc: JdbcClient,
) {
    private lateinit var movie: UUID
    private lateinit var person: UUID

    @BeforeEach
    fun setUp() {
        movie = movies.saveAndFlush(Movie("Arrival", null, null, null, emptyList())).id!!
        person = people.saveAndFlush(Person("Amy Adams", null, null)).id!!
    }

    @Test
    fun `a saved credit reads back with every field`() {
        val saved = credits.saveAndFlush(Credit(movie, person, CreditType.CAST, "Louise Banks", 1))
        entityManager.clear()

        val read = credits.findById(saved.id!!).orElseThrow()

        assertThat(read.movieId).isEqualTo(movie)
        assertThat(read.personId).isEqualTo(person)
        assertThat(read.type).isEqualTo(CreditType.CAST)
        assertThat(read.characterName).isEqualTo("Louise Banks")
        assertThat(read.billingOrder).isEqualTo(1)
        assertThat(jdbc.sql("SELECT type FROM credit WHERE id = ?").param(saved.id).query(String::class.java).single()).isEqualTo("CAST")
    }

    @Test
    fun `one person can be cast and a creator on the same movie, and play two characters`() {
        credits.saveAndFlush(Credit(movie, person, CreditType.CAST, "Louise Banks", 0))
        credits.saveAndFlush(Credit(movie, person, CreditType.CAST, "Narrator", 1))
        credits.saveAndFlush(Credit(movie, person, CreditType.PRODUCER, null, 2))

        assertThat(credits.findAllByMovieIdIn(listOf(movie), Sort.by("billingOrder"))).hasSize(3)
    }

    @Test
    fun `the same role and character twice is a duplicate, reported by the constraint CreditService looks for`() {
        credits.saveAndFlush(Credit(movie, person, CreditType.CAST, "Louise Banks", 0))

        val error = assertThrows<DataIntegrityViolationException> { credits.saveAndFlush(Credit(movie, person, CreditType.CAST, "Louise Banks", 5)) }
        assertThat(constraintName(error)).isEqualTo("credit_unique")
    }

    @Test
    fun `two creator credits for the same role are duplicates even though neither names a character`() {
        credits.saveAndFlush(Credit(movie, person, CreditType.DIRECTOR, null, 0))

        assertThrows<DataIntegrityViolationException> { credits.saveAndFlush(Credit(movie, person, CreditType.DIRECTOR, null, 1)) }
    }

    @Test
    fun `the database refuses a character on a creator credit, even if the service were bypassed`() {
        assertThrows<DataIntegrityViolationException> { credits.saveAndFlush(Credit(movie, person, CreditType.WRITER, "Louise Banks", 0)) }
    }

    @Test
    fun `the database refuses a negative billing order`() {
        assertThrows<DataIntegrityViolationException> { credits.saveAndFlush(Credit(movie, person, CreditType.CAST, null, -1)) }
    }

    @Test
    fun `the database refuses a billing order above 1000, and accepts 1000`() {
        credits.saveAndFlush(Credit(movie, person, CreditType.CAST, "At the limit", 1000))

        assertThrows<DataIntegrityViolationException> { credits.saveAndFlush(Credit(movie, person, CreditType.CAST, null, 1001)) }
    }

    @Test
    fun `a credit for a movie that does not exist is refused by the foreign key CreditService looks for`() {
        val error = assertThrows<DataIntegrityViolationException> {
            credits.saveAndFlush(Credit(UUID.randomUUID(), person, CreditType.CAST, null, 0))
        }
        assertThat(constraintName(error)).isEqualTo("credit_movie_id_fkey")
    }

    @Test
    fun `a credited person cannot be deleted, as reported by the foreign key PersonService looks for`() {
        credits.saveAndFlush(Credit(movie, person, CreditType.CAST, null, 0))
        entityManager.clear()

        val error = assertThrows<DataIntegrityViolationException> {
            people.deleteById(person)
            people.flush()
        }
        assertThat(constraintName(error)).isEqualTo("credit_person_id_fkey")
    }

    @Test
    fun `deleting a movie removes its credits and keeps the person`() {
        credits.saveAndFlush(Credit(movie, person, CreditType.CAST, null, 0))
        entityManager.clear()

        jdbc.sql("DELETE FROM movie WHERE id = ?").param(movie).update()

        assertThat(credits.findAllByPersonIdIn(listOf(person), Sort.by("id"))).isEmpty()
        assertThat(people.existsById(person)).isTrue()
    }

    @Test
    fun `removing reports one credit the first time and none the second`() {
        val id = credits.saveAndFlush(Credit(movie, person, CreditType.CAST, null, 0)).id!!

        assertThat(credits.removeById(id)).isEqualTo(1)
        assertThat(credits.removeById(id)).isZero()
    }

    private fun constraintName(error: DataIntegrityViolationException): String? =
        (error.cause as? org.hibernate.exception.ConstraintViolationException)?.constraintName

    @Test
    fun `lookups by movie and by person return only matching credits, sorted as asked`() {
        val other = people.saveAndFlush(Person("Jeremy Renner", null, null)).id!!
        credits.saveAndFlush(Credit(movie, other, CreditType.CAST, "Ian Donnelly", 2))
        credits.saveAndFlush(Credit(movie, person, CreditType.CAST, "Louise Banks", 1))

        val byMovie = credits.findAllByMovieIdIn(listOf(movie), Sort.by("billingOrder"))
        val byPerson = credits.findAllByPersonIdIn(listOf(other), Sort.by("billingOrder"))

        assertThat(byMovie.map { it.characterName }).containsExactly("Louise Banks", "Ian Donnelly")
        assertThat(byPerson.map { it.characterName }).containsExactly("Ian Donnelly")
    }
}
