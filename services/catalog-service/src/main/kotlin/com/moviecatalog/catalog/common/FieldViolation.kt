package com.moviecatalog.catalog.common

/** One problem with one request field, reported to callers as a google.rpc.BadRequest violation. */
data class FieldViolation(val field: String, val description: String)
