package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Normalises and validates movie fields. Text is trimmed, blank optional text becomes null,
 * and genres are trimmed and de-duplicated case-insensitively. All problems are reported
 * together so a form can show every field error at once. Limits match V1__create_movie.sql.
 */
object MovieValidator {
    const val TITLE_MAX = 200
    const val SYNOPSIS_MAX = 2000
    const val RUNTIME_MAX = 1000
    const val GENRES_MAX = 10
    const val GENRE_MAX = 50
    private val EARLIEST_RELEASE: LocalDate = LocalDate.of(1870, 1, 1)
    private val LATEST_RELEASE: LocalDate = LocalDate.of(2100, 12, 31)

    fun validate(input: MovieInput): MovieDraft {
        val violations = mutableListOf<FieldViolation>()

        val title = input.title.trim()
        if (title.isEmpty()) violations += FieldViolation("title", "is required")
        if (title.length > TITLE_MAX) violations += FieldViolation("title", "must be at most $TITLE_MAX characters")

        val synopsis = input.synopsis?.trim()?.ifEmpty { null }
        if (synopsis != null && synopsis.length > SYNOPSIS_MAX) {
            violations += FieldViolation("synopsis", "must be at most $SYNOPSIS_MAX characters")
        }

        val releaseDate = input.releaseDate?.trim()?.ifEmpty { null }?.let { parseReleaseDate(it, violations) }

        val runtime = input.runtimeMinutes
        if (runtime != null && runtime !in 1..RUNTIME_MAX) {
            violations += FieldViolation("runtime_minutes", "must be between 1 and $RUNTIME_MAX")
        }

        val genres = input.genres.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        if (genres.size > GENRES_MAX) violations += FieldViolation("genres", "must have at most $GENRES_MAX entries")
        if (genres.any { it.length > GENRE_MAX }) {
            violations += FieldViolation("genres", "each genre must be at most $GENRE_MAX characters")
        }

        if (violations.isNotEmpty()) throw InvalidArgumentException(violations)
        return MovieDraft(title, synopsis, releaseDate, runtime, genres)
    }

    private fun parseReleaseDate(value: String, violations: MutableList<FieldViolation>): LocalDate? {
        val date = try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            violations += FieldViolation("release_date", "must be a date in the form yyyy-MM-dd")
            return null
        }
        if (date < EARLIEST_RELEASE || date > LATEST_RELEASE) {
            violations += FieldViolation("release_date", "must be between $EARLIEST_RELEASE and $LATEST_RELEASE")
        }
        return date
    }
}
