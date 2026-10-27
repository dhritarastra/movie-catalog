package com.moviecatalog.catalog.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class RequestArgumentsTest {
    @Test
    fun `parses canonical UUIDs in either case`() {
        val id = UUID.randomUUID()

        assertThat(RequestArguments.uuid("id", id.toString())).isEqualTo(id)
        assertThat(RequestArguments.uuid("id", id.toString().uppercase())).isEqualTo(id)
    }

    @Test
    fun `rejects empty, malformed and shortened UUIDs, naming the field`() {
        listOf("", "not-a-uuid", "1-2-3-4-5").forEach {
            val error = assertThrows<InvalidArgumentException> { RequestArguments.uuid("movie_id", it) }
            assertThat(error.violations).describedAs(it).containsExactly(FieldViolation("movie_id", "must be a UUID"))
        }
    }

    @Test
    fun `accepts versions from 1 and rejects 0, which is what an omitted field looks like`() {
        assertThat(RequestArguments.expectedVersion(1)).isEqualTo(1)

        listOf(0, -1).forEach {
            assertThrows<InvalidArgumentException> { RequestArguments.expectedVersion(it) }
        }
    }

}
