package com.moviecatalog.catalog.common.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Checks an [IsoDate] field and explains the exact problem: the format, the earliest date, the
 * latest date or the future. "Today" comes from Bean Validation's clock (ValidationConfiguration).
 */
class IsoDateValidator : ConstraintValidator<IsoDate, String> {
    private lateinit var min: LocalDate
    private var max: LocalDate? = null
    private var notInFuture = false

    override fun initialize(annotation: IsoDate) {
        min = LocalDate.parse(annotation.min)
        max = annotation.max.ifEmpty { null }?.let(LocalDate::parse)
        notInFuture = annotation.notInFuture
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        val date = try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            return context.reject("must be a date in the form yyyy-MM-dd")
        }
        val latest = max
        return when {
            latest != null && (date < min || date > latest) -> context.reject("must be between $min and $latest")
            date < min -> context.reject("must be on or after $min")
            notInFuture && date > LocalDate.now(context.clockProvider.clock) -> context.reject("cannot be in the future")
            else -> true
        }
    }

    private fun ConstraintValidatorContext.reject(message: String): Boolean {
        disableDefaultConstraintViolation()
        buildConstraintViolationWithTemplate(message).addConstraintViolation()
        return false
    }
}
