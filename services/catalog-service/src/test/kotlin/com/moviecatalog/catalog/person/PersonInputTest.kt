package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import com.moviecatalog.catalog.common.validation.inputValidatorOn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class PersonInputTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val validator = inputValidatorOn(today)
    private val valid = PersonInput("Denis Villeneuve", "Québécois director.", "1967-10-03")

    @Test
    fun `accepts a complete person and a name-only person`() {
        assertDoesNotThrow { validator.check(valid) }
        assertDoesNotThrow { validator.check(PersonInput("Amy Adams", null, null)) }
    }

    @Test
    fun `normalising trims text and turns blank optional text into null`() {
        assertThat(PersonInput.normalised("  Amy Adams ", "  ", " ")).isEqualTo(PersonInput("Amy Adams", null, null))
    }

    @Test
    fun `rejects a blank name`() {
        assertThat(violations(PersonInput.normalised(" \t ", null, null)))
            .containsExactly(FieldViolation("name", "is required"))
    }

    @Test
    fun `accepts a name and a bio at their limits and rejects one character past`() {
        assertDoesNotThrow { validator.check(valid.copy(name = "x".repeat(200), bio = "x".repeat(2000))) }

        assertThat(violations(valid.copy(name = "x".repeat(201))).map { it.field }).containsExactly("name")
        assertThat(violations(valid.copy(bio = "x".repeat(2001))).map { it.field }).containsExactly("bio")
    }

    @Test
    fun `rejects malformed and impossible birth dates`() {
        listOf("03/10/1967", "1967-02-29", "1967-10-32", "yesterday").forEach {
            assertThat(violations(valid.copy(birthDate = it)).map { v -> v.field }).describedAs(it).containsExactly("birth_date")
        }
    }

    @Test
    fun `accepts birth dates from 1800 up to today, and nothing outside that`() {
        assertDoesNotThrow { validator.check(valid.copy(birthDate = "1800-01-01")) }
        assertDoesNotThrow { validator.check(valid.copy(birthDate = today.toString())) }

        assertThat(violations(valid.copy(birthDate = "1799-12-31")))
            .containsExactly(FieldViolation("birth_date", "must be on or after 1800-01-01"))
        assertThat(violations(valid.copy(birthDate = today.plusDays(1).toString())))
            .containsExactly(FieldViolation("birth_date", "cannot be in the future"))
    }

    @Test
    fun `reports every invalid field at once, in form order`() {
        val input = PersonInput("", "x".repeat(2001), "bad")

        assertThat(violations(input).map { it.field }).containsExactly("name", "bio", "birth_date")
    }

    private fun violations(input: PersonInput): List<FieldViolation> =
        assertThrows<InvalidArgumentException> { validator.check(input) }.violations
}
