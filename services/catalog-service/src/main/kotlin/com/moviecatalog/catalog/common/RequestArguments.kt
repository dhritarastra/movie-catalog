package com.moviecatalog.catalog.common

import java.util.UUID

object RequestArguments {
    fun uuid(field: String, value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrNull()
            // fromString also accepts short forms like "1-2-3-4-5".
            ?.takeIf { it.toString() == value.lowercase() }
            ?: throw InvalidArgumentException(listOf(FieldViolation(field, "must be a UUID")))

    /** proto3 sends 0 for a missing field, so versions start at 1. */
    fun expectedVersion(value: Int): Int {
        if (value < 1) throw InvalidArgumentException(listOf(FieldViolation("expected_version", "must be at least 1")))
        return value
    }
}
