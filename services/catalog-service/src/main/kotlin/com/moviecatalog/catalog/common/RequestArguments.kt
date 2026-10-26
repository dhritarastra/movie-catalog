package com.moviecatalog.catalog.common

import java.util.UUID

/** A validated page request (docs/DECISIONS.md D7): values are rejected, never clamped. */
data class Page(val offset: Int, val limit: Int) {
    companion object {
        const val MAX_LIMIT = 100
    }
}

/** Parsing and range checks for identifiers, versions and paging shared by every gRPC endpoint. */
object RequestArguments {
    fun uuid(field: String, value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrNull()
            // UUID.fromString accepts shortened forms such as "1-2-3-4-5"; require the canonical one.
            ?.takeIf { it.toString() == value.lowercase() }
            ?: throw InvalidArgumentException(listOf(FieldViolation(field, "must be a UUID")))

    /** Versions start at 1; 0 is what proto3 sends when the field is omitted (DECISIONS D5). */
    fun expectedVersion(value: Int): Int {
        if (value < 1) throw InvalidArgumentException(listOf(FieldViolation("expected_version", "must be at least 1")))
        return value
    }

    fun page(offset: Int, limit: Int): Page {
        val violations = buildList {
            if (offset < 0) add(FieldViolation("page.offset", "must be 0 or more"))
            if (limit !in 1..Page.MAX_LIMIT) add(FieldViolation("page.limit", "must be between 1 and ${Page.MAX_LIMIT}"))
        }
        if (violations.isNotEmpty()) throw InvalidArgumentException(violations)
        return Page(offset, limit)
    }
}
