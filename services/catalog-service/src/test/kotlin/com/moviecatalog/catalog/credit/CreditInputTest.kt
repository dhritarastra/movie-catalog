package com.moviecatalog.catalog.credit

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import com.moviecatalog.catalog.common.validation.inputValidatorOn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class CreditInputTest {
    private val validator = inputValidatorOn(LocalDate.of(2026, 10, 28))

    @Test
    fun `accepts a cast credit with a character and a creator credit without one`() {
        assertDoesNotThrow { check(CreditInput(CreditType.CAST, "Louise Banks", 0)) }
        assertDoesNotThrow { check(CreditInput(CreditType.DIRECTOR, null, 3)) }
        assertDoesNotThrow { check(CreditInput(CreditType.CAST, null, 1)) }
    }

    @Test
    fun `normalising trims the character name and turns a blank one into null`() {
        assertThat(CreditInput.normalised(CreditType.CAST, "  Louise Banks ", 0).characterName).isEqualTo("Louise Banks")
        assertThat(CreditInput.normalised(CreditType.CAST, " \t ", 0).characterName).isNull()
    }

    @Test
    fun `a blank character name on a creator credit is simply no character`() {
        assertDoesNotThrow { check(CreditInput.normalised(CreditType.WRITER, "   ", 0)) }
    }

    @Test
    fun `a role is required`() {
        assertThat(violations(CreditInput(null, null, 0))).containsExactly(FieldViolation("type", "is required"))
    }

    @Test
    fun `accepts a character name at 200 characters, non-ASCII included, and rejects one past it`() {
        assertDoesNotThrow { check(CreditInput(CreditType.CAST, "É".repeat(200), 0)) }
        assertDoesNotThrow { check(CreditInput(CreditType.CAST, "Amélie 🎬", 0)) }

        assertThat(violations(CreditInput(CreditType.CAST, "x".repeat(201), 0)))
            .containsExactly(FieldViolation("character_name", "must be at most 200 characters"))
    }

    @Test
    fun `accepts billing order from 0 to 1000 and rejects anything outside`() {
        assertDoesNotThrow { check(CreditInput(CreditType.CAST, null, 0)) }
        assertDoesNotThrow { check(CreditInput(CreditType.CAST, null, 1000)) }

        listOf(-1, 1001, Int.MAX_VALUE).forEach {
            assertThat(violations(CreditInput(CreditType.CAST, null, it))).describedAs("$it")
                .containsExactly(FieldViolation("billing_order", "must be between 0 and 1000"))
        }
    }

    @Test
    fun `only cast credits may name a character`() {
        listOf(CreditType.DIRECTOR, CreditType.WRITER, CreditType.PRODUCER).forEach {
            assertThat(violations(CreditInput(it, "Louise Banks", 0))).describedAs("$it")
                .containsExactly(FieldViolation("character_name", "only cast credits have a character"))
        }
    }

    @Test
    fun `reports every invalid field at once, in form order`() {
        val input = CreditInput(CreditType.PRODUCER, "x".repeat(201), -1)

        assertThat(violations(input)).containsExactly(
            FieldViolation("character_name", "must be at most 200 characters"),
            FieldViolation("character_name", "only cast credits have a character"),
            FieldViolation("billing_order", "must be between 0 and 1000"),
        )
    }

    private fun check(input: CreditInput) = validator.check(input, input.crossFieldViolations())

    private fun violations(input: CreditInput): List<FieldViolation> =
        assertThrows<InvalidArgumentException> { check(input) }.violations
}
