package com.moviecatalog.gateway.graphql

// Matches the default in schema.graphqls.
const val DEFAULT_PAGE_SIZE = 20

/** A null offset or limit means the default. */
data class PageInput(val offset: Int?, val limit: Int?)

/** Out-of-range values are passed on for the catalog to reject. */
fun PageInput?.offsetOrDefault(): Int = this?.offset ?: 0

fun PageInput?.limitOrDefault(): Int = this?.limit ?: DEFAULT_PAGE_SIZE
