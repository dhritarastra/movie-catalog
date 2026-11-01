package com.moviecatalog.catalog.common.validation

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class IsoDateValidatorTest {
    private data class Bounded(@field:IsoDate(min = "2000-01-01", max = "2000-12-31") val date: String?)

    private data class Past(@field:IsoDate(min = "2000-01-01", notInFuture = true) val date: String?)

    private val today = LocalDate.of(2026, 10, 28)
    private val validator = inputValidatorOn(today)

    @Test
    fun `a missing date is valid`() {
        assertDoesNotThrow { validator.check(Bounded(null)) }
    }

    @Test
    fun `rejects malformed and impossible dates with the expected format`() {
        listOf("01/02/2000", "2000-1-2", "2000-13-01", "2001-02-29", "2000-04-31", "", "soon").forEach {
            assertThat(violations(Bounded(it))).describedAs(it)
                .containsExactly(FieldViolation("date", "must be a date in the form yyyy-MM-dd"))
        }
    }

    @Test
    fun `accepts a leap day`() {
        assertDoesNotThrow { validator.check(Bounded("2000-02-29")) }
    }

    @Test
    fun `accepts both ends of a range and rejects the day beyond each`() {
        assertDoesNotThrow { validator.check(Bounded("2000-01-01")) }
        assertDoesNotThrow { validator.check(Bounded("2000-12-31")) }

        listOf("1999-12-31", "2001-01-01").forEach {
            assertThat(violations(Bounded(it))).describedAs(it)
                .containsExactly(FieldViolation("date", "must be between 2000-01-01 and 2000-12-31"))
        }
    }

    @Test
    fun `with no upper bound, accepts today and rejects tomorrow and anything before the minimum`() {
        assertDoesNotThrow { validator.check(Past(today.toString())) }

        assertThat(violations(Past(today.plusDays(1).toString())))
            .containsExactly(FieldViolation("date", "cannot be in the future"))
        assertThat(violations(Past("1999-12-31")))
            .containsExactly(FieldViolation("date", "must be on or after 2000-01-01"))
    }

    private fun violations(input: Any): List<FieldViolation> =
        assertThrows<InvalidArgumentException> { validator.check(input) }.violations
}
