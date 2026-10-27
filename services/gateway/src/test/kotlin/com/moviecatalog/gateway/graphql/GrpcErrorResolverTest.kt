package com.moviecatalog.gateway.graphql

import com.google.protobuf.Any
import com.google.rpc.BadRequest
import com.google.rpc.Code
import com.moviecatalog.gateway.grpc.CatalogClient
import com.ninjasquad.springmockk.MockkBean
import io.grpc.Status
import io.grpc.protobuf.StatusProto
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** The mapping from gRPC failures to GraphQL error codes and field errors, with the catalog mocked. */
@SpringBootTest
class GrpcErrorResolverTest(
    @Autowired graphQlService: ExecutionGraphQlService,
) {
    @MockkBean
    private lateinit var catalog: CatalogClient

    private val graphQl = ExecutionGraphQlServiceTester.create(graphQlService)

    @Test
    fun `validation failures become BAD_REQUEST with camelCase field errors`() {
        val badRequest = BadRequest.newBuilder()
            .addFieldViolations(BadRequest.FieldViolation.newBuilder().setField("title").setDescription("is required"))
            .addFieldViolations(BadRequest.FieldViolation.newBuilder().setField("runtime_minutes").setDescription("must be between 1 and 1000"))
            .build()
        every { catalog.createMovie(any()) } throws StatusProto.toStatusRuntimeException(
            com.google.rpc.Status.newBuilder().setCode(Code.INVALID_ARGUMENT_VALUE).setMessage("invalid").addDetails(Any.pack(badRequest)).build(),
        )

        graphQl.document("""mutation { createMovie(input: {title: "", runtimeMinutes: 0, genres: []}) { id } }""")
            .execute()
            .errors().satisfy { errors ->
                val extensions = errors.single().extensions
                assertThat(extensions["code"]).isEqualTo("BAD_REQUEST")
                assertThat(extensions["fieldErrors"]).isEqualTo(
                    listOf(
                        mapOf("field" to "title", "message" to "is required"),
                        mapOf("field" to "runtimeMinutes", "message" to "must be between 1 and 1000"),
                    ),
                )
            }
    }

    @Test
    fun `each gRPC status maps to its GraphQL error code`() {
        mapOf(
            Status.ABORTED to "CONFLICT",
            Status.FAILED_PRECONDITION to "FAILED_PRECONDITION",
            Status.UNAVAILABLE to "SERVICE_UNAVAILABLE",
            Status.DEADLINE_EXCEEDED to "SERVICE_UNAVAILABLE",
            Status.INTERNAL to "INTERNAL_ERROR",
        ).forEach { (status, code) ->
            every { catalog.listMovies(any(), any()) } throws status.withDescription("internal detail").asRuntimeException()

            graphQl.document("{ movies { totalCount } }")
                .execute()
                .errors().satisfy { errors ->
                    assertThat(errors.single().extensions["code"]).describedAs(status.code.name).isEqualTo(code)
                    if (code == "INTERNAL_ERROR") {
                        assertThat(errors.single().message).doesNotContain("internal detail")
                    }
                }
        }
    }
}
