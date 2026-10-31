package com.moviecatalog.gateway.tmdb

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

@Configuration
class TmdbConfiguration {
    @Bean
    fun tmdbRestClient(properties: TmdbProperties): RestClient {
        val http = HttpClient.newBuilder().connectTimeout(properties.timeout).build()
        val requests = JdkClientHttpRequestFactory(http).apply { setReadTimeout(properties.timeout) }
        return RestClient.builder().requestFactory(requests).build()
    }
}
