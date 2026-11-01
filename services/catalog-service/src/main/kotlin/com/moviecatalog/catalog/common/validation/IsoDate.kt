package com.moviecatalog.catalog.common.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/** A yyyy-MM-dd date between [min] and [max], or today with [notInFuture]. Null is allowed. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [IsoDateValidator::class])
@MustBeDocumented
annotation class IsoDate(
    val min: String,
    val max: String = "",
    val notInFuture: Boolean = false,
    // IsoDateValidator replaces this with a more specific message.
    val message: String = "must be a valid date",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
