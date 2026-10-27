package com.moviecatalog.gateway.graphql

/** A failure detected in the gateway itself, before or instead of calling another service. */
class GatewayException(
    val code: ErrorCode,
    message: String,
    val fieldErrors: List<FieldError> = emptyList(),
) : RuntimeException(message)
