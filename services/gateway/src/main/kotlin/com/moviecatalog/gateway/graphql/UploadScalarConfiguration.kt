package com.moviecatalog.gateway.graphql

import graphql.schema.GraphQLScalarType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer

/** The `Upload` scalar from the multipart spec. Files can only be passed as variables. */
@Configuration
class UploadScalarConfiguration {
    @Bean
    fun uploadScalarWiring(): RuntimeWiringConfigurer =
        RuntimeWiringConfigurer { wiring -> wiring.scalar(UPLOAD) }

    companion object {
        val UPLOAD: GraphQLScalarType =
            GraphQLScalarType.newScalar()
                .name("Upload")
                .description("A file sent with the GraphQL multipart request spec.")
                .coercing(UploadCoercing)
                .build()
    }
}
