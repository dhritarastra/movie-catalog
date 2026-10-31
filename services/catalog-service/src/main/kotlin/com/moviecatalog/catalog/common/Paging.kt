package com.moviecatalog.catalog.common

import com.moviecatalog.catalog.config.PagingProperties
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

/** Out-of-range values are rejected, not clamped. */
@Component
class Paging(private val properties: PagingProperties) {
    fun of(offset: Int, limit: Int, sort: Sort): Pageable {
        val violations = buildList {
            if (offset < 0) add(FieldViolation("page.offset", "must be 0 or more"))
            if (limit !in 1..properties.maxLimit) add(FieldViolation("page.limit", "must be between 1 and ${properties.maxLimit}"))
        }
        if (violations.isNotEmpty()) throw InvalidArgumentException(violations)
        return OffsetPageRequest(offset.toLong(), limit, sort)
    }
}
