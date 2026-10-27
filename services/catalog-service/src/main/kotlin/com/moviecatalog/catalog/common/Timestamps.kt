package com.moviecatalog.catalog.common

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The current time at the precision Postgres stores (microseconds), so a timestamp read back
 * from the database is equal to the one that was written.
 */
fun currentTimestamp(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
