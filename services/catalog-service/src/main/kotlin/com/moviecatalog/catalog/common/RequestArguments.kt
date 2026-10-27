package com.moviecatalog.catalog.common

import java.util.UUID

/** Parsing and range checks for identifiers and versions shared by every gRPC endpoint. */
object RequestArguments {
    fun uuid(field: String, value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrNull()
            // UUID.fromString accepts shortened forms such as "1-2-3-4-5"; require the canonical one.
            ?.takeIf { it.toString() == value.lowercase() }
            ?: throw InvalidArgumentException(listOf(FieldViolation(field, "must be a UUID")))

    /** Versions start at 1; 0 is what proto3 sends when the field is omitted, so it is never a version to compare. */
    fun expectedVersion(value: Int): Int {
        if (value < 1) throw InvalidArgumentException(listOf(FieldViolation("expected_version", "must be at least 1")))
        return value
    }
}
