package com.moviecatalog.catalog.common

import com.google.rpc.BadRequest
import io.grpc.Status
import io.grpc.protobuf.StatusProto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.OptimisticLockingFailureException

class CatalogGrpcExceptionHandlerTest {
    private val handler = CatalogGrpcExceptionHandler()

    @Test
    fun `not found and version conflicts map to NOT_FOUND and ABORTED with the message`() {
        val notFound = handler.handleException(NotFoundException("Movie x not found"))!!.status
        assertThat(notFound.code).isEqualTo(Status.Code.NOT_FOUND)
        assertThat(notFound.description).isEqualTo("Movie x not found")
        assertThat(handler.handleException(VersionConflictException("stale"))!!.status.code).isEqualTo(Status.Code.ABORTED)
    }

    @Test
    fun `a write that lost a race is a conflict too`() {
        assertThat(handler.handleException(OptimisticLockingFailureException("lost"))!!.status.code).isEqualTo(Status.Code.ABORTED)
    }

    @Test
    fun `invalid arguments carry a BadRequest detail with every field, in order`() {
        val exception = handler.handleException(
            InvalidArgumentException(listOf(FieldViolation("title", "is required"), FieldViolation("genres", "too many"))),
        )!!

        assertThat(exception.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
        val badRequest = StatusProto.fromThrowable(exception)!!.detailsList.single().unpack(BadRequest::class.java)
        assertThat(badRequest.fieldViolationsList.map { it.field to it.description })
            .containsExactly("title" to "is required", "genres" to "too many")
    }

    @Test
    fun `anything else is left to the default handling`() {
        assertThat(handler.handleException(IllegalStateException("bug"))).isNull()
    }
}
