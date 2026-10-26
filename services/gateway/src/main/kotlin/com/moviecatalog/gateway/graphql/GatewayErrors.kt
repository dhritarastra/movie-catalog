package com.moviecatalog.gateway.graphql

import graphql.ErrorClassification

/**
 * The `extensions.code` values the web app can rely on (docs/PLAN.md §7). Each one tells the UI
 * what to do: show field errors, reload, explain, or offer a retry.
 */
enum class ErrorCode : ErrorClassification {
    BAD_REQUEST,
    NOT_FOUND,
    CONFLICT,
    FAILED_PRECONDITION,
    RESOURCE_EXHAUSTED,

    /** A downstream service did not answer in time. For a mutation the outcome is unknown. */
    SERVICE_UNAVAILABLE,
    INTERNAL_ERROR,
}

/** A field-level problem, named the way the GraphQL schema names the field. */
data class FieldError(val field: String, val message: String)

/** A failure detected in the gateway itself, before or instead of calling another service. */
class GatewayException(
    val code: ErrorCode,
    message: String,
    val fieldErrors: List<FieldError> = emptyList(),
) : RuntimeException(message)
