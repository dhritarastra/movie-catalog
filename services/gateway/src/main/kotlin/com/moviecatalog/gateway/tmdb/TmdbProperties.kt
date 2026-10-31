package com.moviecatalog.gateway.tmdb

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("gateway.tmdb")
data class TmdbProperties(
    // Blank turns poster lookups off.
    val apiToken: String = "",
    val apiUrl: String = "https://api.themoviedb.org/3",
    val imageUrl: String = "https://image.tmdb.org/t/p",
    val timeout: Duration = Duration.ofSeconds(5),
)
