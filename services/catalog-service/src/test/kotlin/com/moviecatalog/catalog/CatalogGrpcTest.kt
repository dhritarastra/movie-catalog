package com.moviecatalog.catalog

import com.google.rpc.BadRequest
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

const val CATALOG_IN_PROCESS_NAME = "catalog-grpc-test"

/**
 * Runs the real catalog in-process against Postgres. Every test class with this annotation has
 * the same configuration, so Spring's context cache gives them one context and one container.
 * Connect with `InProcessChannelBuilder.forName(CATALOG_IN_PROCESS_NAME)`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(properties = ["spring.grpc.server.port=0", "spring.grpc.server.inprocess.name=$CATALOG_IN_PROCESS_NAME"])
@Import(TestcontainersConfiguration::class)
annotation class CatalogGrpcTest

/** The fields named in an INVALID_ARGUMENT error's google.rpc.BadRequest detail, in order. */
fun badRequestFields(error: StatusRuntimeException): List<String> =
    StatusProto.fromThrowable(error)!!.detailsList
        .single { it.`is`(BadRequest::class.java) }
        .unpack(BadRequest::class.java)
        .fieldViolationsList.map { it.field }
