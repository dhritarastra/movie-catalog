package com.moviecatalog.gateway.graphql

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import org.springframework.web.multipart.MultipartFile
import java.util.Locale

/** Accepts only a file passed as a multipart variable. */
internal object UploadCoercing : Coercing<MultipartFile, Nothing> {
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
