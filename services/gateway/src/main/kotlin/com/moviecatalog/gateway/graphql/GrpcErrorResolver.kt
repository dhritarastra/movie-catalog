package com.moviecatalog.gateway.graphql

import com.google.rpc.BadRequest
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import org.slf4j.LoggerFactory
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.stereotype.Component

/**
 * Turns gRPC errors into GraphQL errors with a stable `extensions.code`. Field errors are
 * renamed to camelCase to match the schema. Unexpected errors are logged, not shown.
 */
@Component
class GrpcErrorResolver : DataFetcherExceptionResolverAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? =
        when (ex) {
            is GatewayException -> error(env, ex.code, ex.message ?: ex.code.name, ex.fieldErrors)
            is StatusRuntimeException -> fromStatus(ex, env)
            else -> null
        }

    private fun fromStatus(ex: StatusRuntimeException, env: DataFetchingEnvironment): GraphQLError {
        val description = ex.status.description
        return when (ex.status.code) {
            Status.Code.INVALID_ARGUMENT -> error(env, ErrorCode.BAD_REQUEST, description ?: "Invalid request", fieldErrors(ex))
            Status.Code.NOT_FOUND -> error(env, ErrorCode.NOT_FOUND, description ?: "Not found")
            Status.Code.ALREADY_EXISTS, Status.Code.ABORTED ->
                error(env, ErrorCode.CONFLICT, description ?: "Someone else changed this; reload and try again")
            Status.Code.FAILED_PRECONDITION -> error(env, ErrorCode.FAILED_PRECONDITION, description ?: "Not allowed in the current state")
            Status.Code.RESOURCE_EXHAUSTED -> error(env, ErrorCode.RESOURCE_EXHAUSTED, description ?: "Too large")
            Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> {
                log.warn("Downstream call for {} failed: {}", env.field.name, ex.status)
                error(env, ErrorCode.SERVICE_UNAVAILABLE, "A service did not respond in time. Reload to check whether your change was saved.")
            }
            else -> {
                log.error("Downstream call for {} failed", env.field.name, ex)
                error(env, ErrorCode.INTERNAL_ERROR, "Something went wrong")
            }
        }
    }

    private fun fieldErrors(ex: StatusRuntimeException): List<FieldError> =
        StatusProto.fromThrowable(ex)?.detailsList.orEmpty()
            .filter { it.`is`(BadRequest::class.java) }
            .flatMap { it.unpack(BadRequest::class.java).fieldViolationsList }
            .map { FieldError(toCamelCase(it.field), it.description) }

    private fun error(env: DataFetchingEnvironment, code: ErrorCode, message: String, fieldErrors: List<FieldError> = emptyList()) =
        GraphqlErrorBuilder.newError(env)
            .errorType(code)
            .message(message)
            .extensions(
                buildMap {
                    put("code", code.name)
                    if (fieldErrors.isNotEmpty()) put("fieldErrors", fieldErrors.map { mapOf("field" to it.field, "message" to it.message) })
                },
            )
            .build()

    companion object {
        fun toCamelCase(field: String): String = field.replace(Regex("_([a-z])")) { it.groupValues[1].uppercase() }
    }
}
