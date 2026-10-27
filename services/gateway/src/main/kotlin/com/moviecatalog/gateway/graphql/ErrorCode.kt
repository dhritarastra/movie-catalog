package com.moviecatalog.gateway.graphql

import graphql.ErrorClassification

/**
 * The `extensions.code` values the web app can rely on. Each one tells the UI
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
