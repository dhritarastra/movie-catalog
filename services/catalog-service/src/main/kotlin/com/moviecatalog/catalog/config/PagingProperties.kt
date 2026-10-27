package com.moviecatalog.catalog.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** `catalog.paging.*` in application.yml. */
@ConfigurationProperties("catalog.paging")
data class PagingProperties(
    /** The largest page a caller may ask for. Larger requests are rejected, not cut down. */
    val maxLimit: Int = 100,
)
