package com.moviecatalog.gateway.graphql

const val DEFAULT_PAGE_SIZE = 20

/** `PageInput` as received. An explicit null offset or limit means the default, like an omitted one (DECISIONS D7). */
data class PageInput(val offset: Int?, val limit: Int?)

/** The offset to send to the catalog. Out-of-range values are passed on for the catalog to reject, never adjusted. */
fun PageInput?.offsetOrDefault(): Int = this?.offset ?: 0

fun PageInput?.limitOrDefault(): Int = this?.limit ?: DEFAULT_PAGE_SIZE
