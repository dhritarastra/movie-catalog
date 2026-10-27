package com.moviecatalog.catalog.common.validation

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import org.springframework.stereotype.Component
import kotlin.reflect.full.primaryConstructor

/**
 * Runs Spring's Bean Validation on an input and reports every broken rule at once as an
 * [InvalidArgumentException]. Field names follow the proto fields (snake_case) and come in the
 * order the input declares them, so a form can show and focus its errors predictably.
 */
@Component
class InputValidator(private val validator: Validator) {
    fun check(input: Any) {
        val violations = validator.validate(input)
        if (violations.isEmpty()) return
        val order = input::class.primaryConstructor?.parameters?.map { it.name }.orEmpty()
        throw InvalidArgumentException(
            violations
                .map { FieldViolation(fieldName(it), it.message) }
                .distinct()
                .sortedWith(compareBy({ order.indexOf(toCamelCase(it.field)) }, { it.description })),
        )
    }

    /** `genres[3].<list element>` -> `genres`; `releaseDate` -> `release_date`. */
    private fun fieldName(violation: ConstraintViolation<*>): String =
        violation.propertyPath.first().name.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2].lowercase()}" }

    private fun toCamelCase(field: String): String = field.replace(Regex("_([a-z])")) { it.groupValues[1].uppercase() }
}
