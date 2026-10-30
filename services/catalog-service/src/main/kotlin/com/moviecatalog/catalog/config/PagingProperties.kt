package com.moviecatalog.catalog.config

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** `catalog.paging.*` in application.yml. */
@Validated
@ConfigurationProperties("catalog.paging")
data class PagingProperties(
    /**
     * The largest page a caller may ask for. Larger requests are rejected, not cut down. A value
     * below the pages the clients ask for would break those pages, so it stops startup.
     */
    @field:Min(value = CLIENT_PAGE_SIZE.toLong(), message = "must be at least {value}, the largest page the web app and the gateway ask for")
    val maxLimit: Int = 100,
) {
    companion object {
        /**
         * The people list's PAGE_SIZE in the web app, and the gateway's DEFAULT_PAGE_SIZE, which
         * the person picker uses. The movie list asks for 12.
         */
        const val CLIENT_PAGE_SIZE = 20
    }
}
