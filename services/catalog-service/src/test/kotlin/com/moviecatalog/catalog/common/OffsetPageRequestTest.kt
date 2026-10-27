package com.moviecatalog.catalog.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort

class OffsetPageRequestTest {
    private val sort = Sort.by("id")

    @Test
    fun `next and previous move by one limit from any offset`() {
        val page = OffsetPageRequest(7, 5, sort)

        assertThat(page.next().offset).isEqualTo(12)
        assertThat(page.previousOrFirst().offset).isEqualTo(2)
        assertThat(page.first().offset).isZero()
        assertThat(page.hasPrevious()).isTrue()
    }

    @Test
    fun `previous never goes below the first row`() {
        val page = OffsetPageRequest(3, 5, sort)

        assertThat(page.previousOrFirst().offset).isZero()
        assertThat(OffsetPageRequest(0, 5, sort).hasPrevious()).isFalse()
    }

    @Test
    fun `page numbers count whole limits`() {
        assertThat(OffsetPageRequest(12, 5, sort).pageNumber).isEqualTo(2)
        assertThat(OffsetPageRequest(0, 5, sort).withPage(3).offset).isEqualTo(15)
    }
}
