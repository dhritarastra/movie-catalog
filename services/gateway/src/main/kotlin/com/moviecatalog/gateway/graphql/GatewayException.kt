package com.moviecatalog.gateway.graphql

/** An error found by the gateway itself, without calling another service. */
class GatewayException(
    val code: ErrorCode,
    message: String,
    val fieldErrors: List<FieldError> = emptyList(),
) : RuntimeException(message)
