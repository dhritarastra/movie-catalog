package com.moviecatalog.catalog.common.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * A calendar date written as yyyy-MM-dd, from [min] up to [max] (when set) or today (when
 * [notInFuture]). Dates travel as ISO strings in the API, so they are checked before parsing.
 * A null value is valid; leave it out with a @NotNull where a date is required.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [IsoDateValidator::class])
@MustBeDocumented
annotation class IsoDate(
    val min: String,
    val max: String = "",
    val notInFuture: Boolean = false,
    // Replaced by a message for the exact problem; see IsoDateValidator.
    val message: String = "must be a valid date",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
