package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import com.moviecatalog.catalog.common.validation.inputValidatorOn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class MovieInputTest {
    private val validator = inputValidatorOn(LocalDate.of(2026, 10, 28))
    private val valid = MovieInput("Arrival", "Linguist meets heptapods.", "2016-11-11", 116, listOf("Sci-Fi", "Drama"))

    @Test
    fun `accepts a complete movie and a title-only movie`() {
        assertDoesNotThrow { validator.check(valid) }
        assertDoesNotThrow { validator.check(MovieInput("Arrival", null, null, null, emptyList())) }
    }

    @Test
    fun `normalising trims text and turns blank optional text into null`() {
        val input = MovieInput.normalised("  Arrival  ", "   ", " ", 116, emptyList())

        assertThat(input).isEqualTo(MovieInput("Arrival", null, null, 116, emptyList()))
    }

    @Test
    fun `normalising trims genres, drops blank ones and de-duplicates ignoring case, keeping the first spelling`() {
        val input = MovieInput.normalised("Arrival", null, null, null, listOf(" Drama ", "", "drama", "Sci-Fi", "  "))

        assertThat(input.genres).containsExactly("Drama", "Sci-Fi")
    }

    @Test
    fun `keeps non-ASCII text as written`() {
        val input = MovieInput.normalised(" Amélie 🎬 ", null, null, null, listOf("Comédie"))

        assertThat(input.title).isEqualTo("Amélie 🎬")
        assertDoesNotThrow { validator.check(input) }
    }

    @Test
    fun `rejects a blank title`() {
        assertThat(violations(MovieInput.normalised(" \t ", null, null, null, emptyList())))
            .containsExactly(FieldViolation("title", "is required"))
    }

    @Test
    fun `accepts a title and a synopsis at their limits and rejects one character past`() {
        assertDoesNotThrow { validator.check(valid.copy(title = "x".repeat(200), synopsis = "x".repeat(2000))) }

        assertThat(violations(valid.copy(title = "x".repeat(201))))
            .containsExactly(FieldViolation("title", "must be at most 200 characters"))
        assertThat(violations(valid.copy(synopsis = "x".repeat(2001))))
            .containsExactly(FieldViolation("synopsis", "must be at most 2000 characters"))
    }

    @Test
    fun `accepts release dates from 1870 to 2100 and rejects impossible or out-of-range ones`() {
        assertDoesNotThrow { validator.check(valid.copy(releaseDate = "1870-01-01")) }
        assertDoesNotThrow { validator.check(valid.copy(releaseDate = "2100-12-31")) }

        listOf("11/11/2016", "2016-13-01", "2016-02-30", "1869-12-31", "2101-01-01").forEach {
            assertThat(violations(valid.copy(releaseDate = it)).map { v -> v.field }).describedAs(it).containsExactly("release_date")
        }
    }

    @Test
    fun `accepts runtimes from 1 to 1000 minutes only`() {
        assertDoesNotThrow { validator.check(valid.copy(runtimeMinutes = 1)) }
        assertDoesNotThrow { validator.check(valid.copy(runtimeMinutes = 1000)) }

        listOf(0, -5, 1001).forEach {
            assertThat(violations(valid.copy(runtimeMinutes = it))).describedAs("$it")
                .containsExactly(FieldViolation("runtime_minutes", "must be between 1 and 1000"))
        }
    }

    @Test
    fun `accepts 10 genres of up to 50 characters, and rejects more or longer`() {
        assertDoesNotThrow { validator.check(valid.copy(genres = (1..10).map { "x".repeat(49) + it % 10 })) }

        assertThat(violations(valid.copy(genres = (1..11).map { "Genre $it" })))
            .containsExactly(FieldViolation("genres", "must have at most 10 entries"))
        assertThat(violations(valid.copy(genres = listOf("x".repeat(51)))))
            .containsExactly(FieldViolation("genres", "each genre must be at most 50 characters"))
    }

    @Test
    fun `reports every invalid field at once, in form order`() {
        val input = MovieInput("", null, "bad", 0, emptyList())

        assertThat(violations(input).map { it.field }).containsExactly("title", "release_date", "runtime_minutes")
    }

    private fun violations(input: MovieInput): List<FieldViolation> =
        assertThrows<InvalidArgumentException> { validator.check(input) }.violations
}
