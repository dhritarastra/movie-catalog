package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import com.moviecatalog.catalog.common.parseIsoDate
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Normalises and validates person fields the same way as [com.moviecatalog.catalog.movie.MovieValidator]:
 * text is trimmed, blank optional text becomes null, and every problem is reported at once.
 * Limits match V2__create_person.sql. Names are not unique; two people can share one.
 */
object PersonValidator {
    const val NAME_MAX = 200
    const val BIO_MAX = 2000
    val EARLIEST_BIRTH: LocalDate = LocalDate.of(1800, 1, 1)

    // The first time zone to reach each new day. Using its date as "today" means a birth date
    // that is today for the user is never rejected as being in the future, wherever they are.
    private val EARLIEST_ZONE = ZoneOffset.ofHours(14)

    fun validate(input: PersonInput, today: LocalDate = LocalDate.now(EARLIEST_ZONE)): PersonDraft {
        val violations = mutableListOf<FieldViolation>()

        val name = input.name.trim()
        if (name.isEmpty()) violations += FieldViolation("name", "is required")
        if (name.length > NAME_MAX) violations += FieldViolation("name", "must be at most $NAME_MAX characters")

        val bio = input.bio?.trim()?.ifEmpty { null }
        if (bio != null && bio.length > BIO_MAX) violations += FieldViolation("bio", "must be at most $BIO_MAX characters")

        val birthDate = input.birthDate?.trim()?.ifEmpty { null }?.let { parseIsoDate("birth_date", it, violations) }
        if (birthDate != null && birthDate < EARLIEST_BIRTH) {
            violations += FieldViolation("birth_date", "must be on or after $EARLIEST_BIRTH")
        }
        if (birthDate != null && birthDate > today) violations += FieldViolation("birth_date", "cannot be in the future")

        if (violations.isNotEmpty()) throw InvalidArgumentException(violations)
        return PersonDraft(name, bio, birthDate)
    }
}
