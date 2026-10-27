package com.moviecatalog.catalog.common

import com.google.protobuf.Any
import com.google.rpc.BadRequest
import com.google.rpc.Code
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.protobuf.StatusProto
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.grpc.server.exception.GrpcExceptionHandler
import org.springframework.stereotype.Component

/**
 * Translates [CatalogException]s into gRPC statuses. Validation failures carry a
 * google.rpc.BadRequest detail listing each field, so the gateway can show per-field errors.
 * Returning null leaves other exceptions to Spring gRPC's default handling (UNKNOWN).
 */
@Component
class CatalogGrpcExceptionHandler : GrpcExceptionHandler {
    override fun handleException(exception: Throwable): StatusException? =
        when (exception) {
            is NotFoundException -> Status.NOT_FOUND.withDescription(exception.message).asException()
            is VersionConflictException -> Status.ABORTED.withDescription(exception.message).asException()
            is InvalidArgumentException -> invalidArgument(exception)
            // Two writes raced and this one lost: the row changed after it was read.
            is OptimisticLockingFailureException ->
                Status.ABORTED.withDescription("Someone else changed this at the same moment; reload and try again").asException()
            else -> null
        }

    private fun invalidArgument(exception: InvalidArgumentException): StatusException {
        val badRequest = BadRequest.newBuilder()
            .addAllFieldViolations(
                exception.violations.map {
                    BadRequest.FieldViolation.newBuilder().setField(it.field).setDescription(it.description).build()
                },
            )
            .build()
        val status = com.google.rpc.Status.newBuilder()
            .setCode(Code.INVALID_ARGUMENT_VALUE)
            .setMessage(exception.message ?: "Invalid request")
            .addDetails(Any.pack(badRequest))
            .build()
        return StatusProto.toStatusException(status)
    }
}
