package com.moviecatalog.catalog.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** `catalog.search.*` in application.yml. */
@ConfigurationProperties("catalog.search")
data class SearchProperties(
    /** The most results one search may ask for. Larger requests are rejected, not cut down. */
    val maxLimit: Int = 50,
)
