package com.moviecatalog.gateway.graphql

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import org.springframework.web.multipart.MultipartFile
import java.util.Locale

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

private object UploadCoercing : Coercing<MultipartFile, Nothing> {
    override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): MultipartFile =
        input as? MultipartFile
            ?: throw CoercingParseValueException("Upload must be a file sent as a multipart request part")

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): MultipartFile = throw CoercingParseLiteralException("Upload cannot be written inline; send it as a variable")

    override fun serialize(dataFetcherResult: Any, graphQLContext: GraphQLContext, locale: Locale): Nothing =
        throw CoercingSerializeException("Upload is an input-only scalar")
}
