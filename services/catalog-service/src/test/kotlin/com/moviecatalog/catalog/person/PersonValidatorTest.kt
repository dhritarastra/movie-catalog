package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.ZoneOffset

class PersonValidatorTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val valid = PersonInput(name = "Denis Villeneuve", bio = "Québécois director.", birthDate = "1967-10-03")

    @Test
    fun `accepts a complete person`() {
        assertThat(validate(valid)).isEqualTo(PersonDraft("Denis Villeneuve", "Québécois director.", LocalDate.of(1967, 10, 3)))
    }

    @Test
    fun `accepts a name-only person`() {
        assertThat(validate(PersonInput("Amy Adams", null, null))).isEqualTo(PersonDraft("Amy Adams", null, null))
    }

    @Test
    fun `trims text and turns blank optional text into null`() {
        val draft = validate(valid.copy(name = "  Amy Adams ", bio = "  ", birthDate = " "))

        assertThat(draft).isEqualTo(PersonDraft("Amy Adams", null, null))
    }

    @Test
    fun `rejects a blank name`() {
        assertThat(violations(valid.copy(name = " \t "))).containsExactly(FieldViolation("name", "is required"))
    }

    @Test
    fun `accepts a name at the length limit and rejects one past it`() {
        validate(valid.copy(name = "x".repeat(PersonValidator.NAME_MAX)))

        assertThat(violations(valid.copy(name = "x".repeat(PersonValidator.NAME_MAX + 1))).map { it.field }).containsExactly("name")
    }

    @Test
    fun `accepts a bio at the length limit and rejects one past it`() {
        validate(valid.copy(bio = "x".repeat(PersonValidator.BIO_MAX)))

        assertThat(violations(valid.copy(bio = "x".repeat(PersonValidator.BIO_MAX + 1))).map { it.field }).containsExactly("bio")
    }

    @Test
    fun `rejects malformed and impossible dates`() {
        listOf("03/10/1967", "1967-02-29", "1967-10-32", "yesterday").forEach {
            assertThat(violations(valid.copy(birthDate = it)).map { v -> v.field }).describedAs(it).containsExactly("birth_date")
        }
    }

    @Test
    fun `accepts birth dates from 1800 up to today, and nothing outside that`() {
        validate(valid.copy(birthDate = "1800-01-01"))
        validate(valid.copy(birthDate = today.toString()))

        assertThat(violations(valid.copy(birthDate = "1799-12-31")))
            .containsExactly(FieldViolation("birth_date", "must be on or after 1800-01-01"))
        assertThat(violations(valid.copy(birthDate = today.plusDays(1).toString())))
            .containsExactly(FieldViolation("birth_date", "cannot be in the future"))
    }

    @Test
    fun `by default, today is the date in the first time zone to reach it`() {
        // Already tomorrow in UTC for part of each day, so a UTC "today" would wrongly reject it.
        val todayInKiribati = LocalDate.now(ZoneOffset.ofHours(14))

        PersonValidator.validate(valid.copy(birthDate = todayInKiribati.toString()))
    }

    @Test
    fun `reports every invalid field at once`() {
        val input = PersonInput(name = "", bio = "x".repeat(PersonValidator.BIO_MAX + 1), birthDate = "bad")

        assertThat(violations(input).map { it.field }).containsExactly("name", "bio", "birth_date")
    }

    private fun validate(input: PersonInput) = PersonValidator.validate(input, today)

    private fun violations(input: PersonInput): List<FieldViolation> =
        assertThrows<InvalidArgumentException> { validate(input) }.violations
}
