package com.moviecatalog.catalog.common

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/**
 * A [Pageable] that starts at any offset. Spring's PageRequest only takes page numbers, but the
 * API pages by offset and limit, and an offset need not be a multiple of the limit.
 */
class OffsetPageRequest(private val offset: Long, private val limit: Int, private val sort: Sort) : Pageable {
    override fun getPageNumber(): Int = (offset / limit).toInt()

    override fun getPageSize(): Int = limit

    override fun getOffset(): Long = offset

    override fun getSort(): Sort = sort

    override fun next(): Pageable = OffsetPageRequest(offset + limit, limit, sort)

    override fun previousOrFirst(): Pageable = OffsetPageRequest(maxOf(0, offset - limit), limit, sort)

    override fun first(): Pageable = OffsetPageRequest(0, limit, sort)

    override fun withPage(pageNumber: Int): Pageable = OffsetPageRequest(pageNumber.toLong() * limit, limit, sort)

    override fun hasPrevious(): Boolean = offset > 0
}
