package com.moviecatalog.catalog.common.validation

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class InputValidatorTest {
    private data class Input(
        @field:Size(max = 3, message = "too long")
        val zebraName: String,
        @field:NotBlank(message = "is required")
        @field:Size(max = 3, message = "too long")
        val alpha: String,
        val tags: List<@Size(max = 2, message = "tag too long") String>,
    )

    private val validator = inputValidatorOn(LocalDate.of(2026, 10, 28))

    @Test
    fun `a valid input passes`() {
        assertDoesNotThrow { validator.check(Input("abc", "abc", listOf("ab"))) }
    }

    @Test
    fun `reports every field in declaration order, not alphabetically, with snake_case names`() {
        val error = assertThrows<InvalidArgumentException> { validator.check(Input("abcd", "", listOf("abc"))) }

        assertThat(error.violations).containsExactly(
            FieldViolation("zebra_name", "too long"),
            FieldViolation("alpha", "is required"),
            FieldViolation("tags", "tag too long"),
        )
    }

    @Test
    fun `two broken rules on one field are both reported, in a stable order`() {
        val error = assertThrows<InvalidArgumentException> { validator.check(Input("abc", " \t ", listOf())) }

        assertThat(error.violations).containsExactly(FieldViolation("alpha", "is required"))

        val both = assertThrows<InvalidArgumentException> { validator.check(Input("abc", "    ", listOf())) }
        assertThat(both.violations).containsExactly(FieldViolation("alpha", "is required"), FieldViolation("alpha", "too long"))
    }

    @Test
    fun `extra violations from rules an annotation cannot express are merged in form order`() {
        val extra = listOf(FieldViolation("zebra_name", "clashes with alpha"))

        val error = assertThrows<InvalidArgumentException> { validator.check(Input("abc", "", listOf()), extra) }

        assertThat(error.violations).containsExactly(FieldViolation("zebra_name", "clashes with alpha"), FieldViolation("alpha", "is required"))
        assertThrows<InvalidArgumentException> { validator.check(Input("abc", "abc", listOf()), extra) }
    }

    @Test
    fun `the same problem on several list entries is reported once`() {
        val error = assertThrows<InvalidArgumentException> { validator.check(Input("abc", "abc", listOf("abc", "abcd"))) }

        assertThat(error.violations).containsExactly(FieldViolation("tags", "tag too long"))
    }
}
