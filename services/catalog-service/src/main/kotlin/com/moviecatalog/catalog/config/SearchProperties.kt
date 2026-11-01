package com.moviecatalog.catalog.config

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("catalog.search")
data class SearchProperties(
    // Must cover the number of results the clients ask for.
    @field:Min(value = CLIENT_LIMIT.toLong(), message = "must be at least {value}, the number of results the web app and the gateway ask for")
    val maxLimit: Int = 50,
) {
    companion object {
        // Default search limit in the web app and the gateway.
        const val CLIENT_LIMIT = 10
    }
}
