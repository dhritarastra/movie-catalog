package com.moviecatalog.gateway.graphql

import com.google.protobuf.Timestamp
import java.time.Instant

/** GraphQL timestamps are ISO-8601 instants. */
fun Timestamp.toIsoString(): String = Instant.ofEpochSecond(seconds, nanos.toLong()).toString()
