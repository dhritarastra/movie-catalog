package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class MovieValidatorTest {
    private val valid = MovieInput(
        title = "Arrival",
        synopsis = "Linguist meets heptapods.",
        releaseDate = "2016-11-11",
        runtimeMinutes = 116,
        genres = listOf("Sci-Fi", "Drama"),
    )

    @Test
    fun `accepts a complete movie`() {
        val draft = MovieValidator.validate(valid)

        assertThat(draft).isEqualTo(
            MovieDraft("Arrival", "Linguist meets heptapods.", LocalDate.of(2016, 11, 11), 116, listOf("Sci-Fi", "Drama")),
        )
    }

    @Test
    fun `accepts a title-only movie`() {
        val draft = MovieValidator.validate(MovieInput("Arrival", null, null, null, emptyList()))

        assertThat(draft).isEqualTo(MovieDraft("Arrival", null, null, null, emptyList()))
    }

    @Test
    fun `trims text and turns blank optional text into null`() {
        val draft = MovieValidator.validate(valid.copy(title = "  Arrival  ", synopsis = "   ", releaseDate = " "))

        assertThat(draft.title).isEqualTo("Arrival")
        assertThat(draft.synopsis).isNull()
        assertThat(draft.releaseDate).isNull()
    }

    @Test
    fun `trims genres, drops blank ones and de-duplicates ignoring case`() {
        val draft = MovieValidator.validate(valid.copy(genres = listOf(" Drama ", "", "drama", "Sci-Fi", "  ")))

        assertThat(draft.genres).containsExactly("Drama", "Sci-Fi")
    }

    @Test
    fun `rejects a blank title`() {
        assertThat(violations(valid.copy(title = "   "))).containsExactly(FieldViolation("title", "is required"))
    }

    @Test
    fun `accepts a title at the length limit and rejects one past it`() {
        MovieValidator.validate(valid.copy(title = "x".repeat(MovieValidator.TITLE_MAX)))

        assertThat(violations(valid.copy(title = "x".repeat(MovieValidator.TITLE_MAX + 1))).map { it.field })
            .containsExactly("title")
    }

    @Test
    fun `rejects an over-long synopsis`() {
        assertThat(violations(valid.copy(synopsis = "x".repeat(MovieValidator.SYNOPSIS_MAX + 1))).map { it.field })
            .containsExactly("synopsis")
    }

    @Test
    fun `rejects malformed and impossible dates`() {
        listOf("11/11/2016", "2016-13-01", "2016-02-30", "not a date").forEach {
            assertThat(violations(valid.copy(releaseDate = it)).map { v -> v.field }).describedAs(it)
                .containsExactly("release_date")
        }
    }

    @Test
    fun `rejects dates outside the supported range`() {
        assertThat(violations(valid.copy(releaseDate = "1869-12-31")).map { it.field }).containsExactly("release_date")
        assertThat(violations(valid.copy(releaseDate = "2101-01-01")).map { it.field }).containsExactly("release_date")
        MovieValidator.validate(valid.copy(releaseDate = "1870-01-01"))
    }

    @Test
    fun `rejects runtimes outside 1 to 1000 minutes`() {
        listOf(0, -5, MovieValidator.RUNTIME_MAX + 1).forEach {
            assertThat(violations(valid.copy(runtimeMinutes = it)).map { v -> v.field }).describedAs("$it")
                .containsExactly("runtime_minutes")
        }
        MovieValidator.validate(valid.copy(runtimeMinutes = 1))
        MovieValidator.validate(valid.copy(runtimeMinutes = MovieValidator.RUNTIME_MAX))
    }

    @Test
    fun `rejects too many genres and over-long genre names`() {
        val tooMany = (1..MovieValidator.GENRES_MAX + 1).map { "Genre $it" }
        assertThat(violations(valid.copy(genres = tooMany)).map { it.field }).containsExactly("genres")

        val tooLong = listOf("x".repeat(MovieValidator.GENRE_MAX + 1))
        assertThat(violations(valid.copy(genres = tooLong)).map { it.field }).containsExactly("genres")
    }

    @Test
    fun `reports every invalid field at once`() {
        val input = MovieInput(title = "", synopsis = null, releaseDate = "bad", runtimeMinutes = 0, genres = emptyList())

        assertThat(violations(input).map { it.field }).containsExactly("title", "release_date", "runtime_minutes")
    }

    private fun violations(input: MovieInput): List<FieldViolation> =
        assertThrows<InvalidArgumentException> { MovieValidator.validate(input) }.violations
}
