package com.moviecatalog.catalog.common.validation

import jakarta.validation.Validation
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

fun inputValidatorOn(today: LocalDate): InputValidator {
    val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    val factory = Validation.byDefaultProvider().configure().clockProvider { clock }.buildValidatorFactory()
    return InputValidator(factory.validator)
}
