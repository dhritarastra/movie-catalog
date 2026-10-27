package com.moviecatalog.catalog.common.validation

import jakarta.validation.Validation
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** An [InputValidator] whose "today" is [today], so date rules are tested without depending on the real clock. */
fun inputValidatorOn(today: LocalDate): InputValidator {
    val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    val factory = Validation.byDefaultProvider().configure().clockProvider { clock }.buildValidatorFactory()
    return InputValidator(factory.validator)
}
