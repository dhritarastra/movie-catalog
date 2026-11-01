package com.moviecatalog.catalog.config

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("catalog.paging")
data class PagingProperties(
    // Must cover the largest page the clients ask for.
    @field:Min(value = CLIENT_PAGE_SIZE.toLong(), message = "must be at least {value}, the largest page the web app and the gateway ask for")
    val maxLimit: Int = 100,
) {
    companion object {
        // Page size of the web people list and the gateway default.
        const val CLIENT_PAGE_SIZE = 20
    }
}
