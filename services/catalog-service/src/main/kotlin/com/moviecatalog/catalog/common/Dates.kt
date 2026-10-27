package com.moviecatalog.catalog.common

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Parses an ISO-8601 calendar date (yyyy-MM-dd), or records a violation for [field] and returns null. */
fun parseIsoDate(field: String, value: String, violations: MutableList<FieldViolation>): LocalDate? =
    try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        violations += FieldViolation(field, "must be a date in the form yyyy-MM-dd")
        null
    }
