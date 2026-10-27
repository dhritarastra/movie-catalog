package com.moviecatalog.catalog.common

import com.moviecatalog.catalog.config.PagingProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Sort

class PagingTest {
    private val paging = Paging(PagingProperties(maxLimit = 100))
    private val sort = Sort.by("id")

    @Test
    fun `accepts bounds exactly at the limits and keeps the offset as given`() {
        val first = paging.of(0, 1, sort)
        assertThat(first.offset).isZero()
        assertThat(first.pageSize).isEqualTo(1)

        // 7 is not a multiple of 100: an offset page starts exactly where the caller asked.
        val last = paging.of(7, 100, sort)
        assertThat(last.offset).isEqualTo(7)
        assertThat(last.pageSize).isEqualTo(100)
        assertThat(last.sort).isEqualTo(sort)
    }

    @Test
    fun `rejects a limit of 0, which is what an omitted limit looks like, and one past the maximum`() {
        listOf(0, 101).forEach {
            val error = assertThrows<InvalidArgumentException> { paging.of(0, it, sort) }
            assertThat(error.violations).describedAs("limit $it")
                .containsExactly(FieldViolation("page.limit", "must be between 1 and 100"))
        }
    }

    @Test
    fun `reports a negative offset and a negative limit together instead of clamping them`() {
        val error = assertThrows<InvalidArgumentException> { paging.of(-1, -1, sort) }

        assertThat(error.violations.map { it.field }).containsExactly("page.offset", "page.limit")
    }

    @Test
    fun `the maximum comes from configuration`() {
        val small = Paging(PagingProperties(maxLimit = 5))

        assertThat(small.of(0, 5, sort).pageSize).isEqualTo(5)
        assertThrows<InvalidArgumentException> { small.of(0, 6, sort) }
    }
}
