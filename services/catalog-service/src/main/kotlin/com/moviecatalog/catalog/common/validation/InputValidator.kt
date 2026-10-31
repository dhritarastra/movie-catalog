package com.moviecatalog.catalog.common.validation

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import org.springframework.stereotype.Component
import kotlin.reflect.full.primaryConstructor

/** Reports every violation at once, using the proto field names, in declaration order. */
@Component
class InputValidator(private val validator: Validator) {
    fun check(input: Any, extra: List<FieldViolation> = emptyList()) {
        val violations = validator.validate(input).map { FieldViolation(fieldName(it), it.message) } + extra
        if (violations.isEmpty()) return
        val order = input::class.primaryConstructor?.parameters?.map { it.name }.orEmpty()
        throw InvalidArgumentException(
            violations
                .distinct()
                .sortedWith(compareBy({ order.indexOf(toCamelCase(it.field)) }, { it.description })),
        )
    }

    // "genres[3]" -> "genres", "releaseDate" -> "release_date"
    private fun fieldName(violation: ConstraintViolation<*>): String =
        violation.propertyPath.first().name.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2].lowercase()}" }

    private fun toCamelCase(field: String): String = field.replace(Regex("_([a-z])")) { it.groupValues[1].uppercase() }
}
