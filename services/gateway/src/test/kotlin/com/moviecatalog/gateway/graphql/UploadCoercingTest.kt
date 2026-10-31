package com.moviecatalog.gateway.graphql

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockMultipartFile
import java.util.Locale

class UploadCoercingTest {
    private val context = GraphQLContext.getDefault()

    @Test
    fun `accepts a file placed in the variables by the multipart handler`() {
        val file = MockMultipartFile("0", "poster.png", "image/png", byteArrayOf(1))

        assertThat(UploadCoercing.parseValue(file, context, Locale.ROOT)).isSameAs(file)
    }

    @Test
    fun `rejects any other variable value, such as a string`() {
        assertThrows<CoercingParseValueException> { UploadCoercing.parseValue("poster.png", context, Locale.ROOT) }
    }

    @Test
    fun `cannot be written inline in a query`() {
        assertThrows<CoercingParseLiteralException> {
            UploadCoercing.parseLiteral(StringValue("x"), CoercedVariables.emptyVariables(), context, Locale.ROOT)
        }
    }

    @Test
    fun `is input-only, so it is never sent in a response`() {
        assertThrows<CoercingSerializeException> { UploadCoercing.serialize("anything", context, Locale.ROOT) }
    }
}
