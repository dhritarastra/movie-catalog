package com.moviecatalog.gateway.graphql

/** A field-level problem, named the way the GraphQL schema names the field. */
data class FieldError(val field: String, val message: String)
