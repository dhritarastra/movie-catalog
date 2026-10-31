package com.moviecatalog.gateway.graphql

import graphql.ErrorClassification

/** The `extensions.code` values the web app relies on. */
enum class ErrorCode : ErrorClassification {
    BAD_REQUEST,
    NOT_FOUND,
    CONFLICT,
    FAILED_PRECONDITION,
    RESOURCE_EXHAUSTED,

    // A service did not answer in time; a mutation may or may not have happened.
    SERVICE_UNAVAILABLE,

    // TMDB failed; nothing was changed.
    TMDB_UNAVAILABLE,
    INTERNAL_ERROR,
}
