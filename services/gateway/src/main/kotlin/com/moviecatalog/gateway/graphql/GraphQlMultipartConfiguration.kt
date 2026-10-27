package com.moviecatalog.gateway.graphql

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.RequestPredicates
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.RouterFunctions
import org.springframework.web.servlet.function.ServerResponse

/**
 * Routes multipart POSTs to /graphql to [GraphQlMultipartHandler]. Spring for GraphQL serves the
 * same path with its own router function, which answers any non-JSON body with 415, so this route
 * is ordered ahead of it and matches only multipart/form-data. JSON requests still go to Spring's.
 */
@Configuration
class GraphQlMultipartConfiguration {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun graphQlMultipartRoute(handler: GraphQlMultipartHandler): RouterFunction<ServerResponse> =
        RouterFunctions.route()
            .POST("/graphql", RequestPredicates.contentType(MediaType.MULTIPART_FORM_DATA), handler::handle)
            .build()
}
