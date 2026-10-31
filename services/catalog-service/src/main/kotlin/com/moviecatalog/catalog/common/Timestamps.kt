package com.moviecatalog.catalog.common

import java.time.Instant
import java.time.temporal.ChronoUnit

/** Postgres stores microseconds, so round to what will be read back. */
fun currentTimestamp(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
