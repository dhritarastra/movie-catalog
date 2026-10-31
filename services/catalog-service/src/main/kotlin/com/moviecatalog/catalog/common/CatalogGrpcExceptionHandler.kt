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

/** Maps catalog errors to gRPC statuses; anything else falls through to Spring gRPC. */
@Component
class CatalogGrpcExceptionHandler : GrpcExceptionHandler {
    override fun handleException(exception: Throwable): StatusException? =
        when (exception) {
            is NotFoundException -> Status.NOT_FOUND.withDescription(exception.message).asException()
            is VersionConflictException -> Status.ABORTED.withDescription(exception.message).asException()
            is AlreadyExistsException -> Status.ALREADY_EXISTS.withDescription(exception.message).asException()
            is FailedPreconditionException -> Status.FAILED_PRECONDITION.withDescription(exception.message).asException()
            is InvalidArgumentException -> invalidArgument(exception)
            // Another write changed the row after we read it.
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
