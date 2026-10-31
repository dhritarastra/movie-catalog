package com.moviecatalog.gateway.graphql

import com.google.protobuf.Timestamp
import java.time.Instant

fun Timestamp.toIsoString(): String = Instant.ofEpochSecond(seconds, nanos.toLong()).toString()
