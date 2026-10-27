package com.moviecatalog.gateway.graphql

import graphql.schema.GraphQLScalarType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer

/**
 * The `Upload` scalar from the GraphQL multipart request spec. The multipart adapter places
 * each uploaded file into the operation's variables, so the only valid input is a
 * [MultipartFile] supplied as a variable. Uploads are input-only and cannot be written inline
 * in a query, so serializing or parsing a literal is an error.
 */
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
