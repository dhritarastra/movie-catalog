package com.moviecatalog.catalog.config

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** `catalog.search.*` in application.yml. */
@Validated
@ConfigurationProperties("catalog.search")
data class SearchProperties(
    /**
     * The most results one search may ask for. Larger requests are rejected, not cut down. A
     * value below what the clients ask for would make every search fail, so it stops startup.
     */
    @field:Min(value = CLIENT_LIMIT.toLong(), message = "must be at least {value}, the number of results the web app and the gateway ask for")
    val maxLimit: Int = 50,
) {
    companion object {
        /** SEARCH_LIMIT in web/src/lib/search.ts and the gateway's DEFAULT_SEARCH_LIMIT. */
        const val CLIENT_LIMIT = 10
    }
}
