package com.moviecatalog.catalog

import com.google.rpc.BadRequest
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

const val CATALOG_IN_PROCESS_NAME = "catalog-grpc-test"

/** Same config on every test class, so they share one Spring context and one Postgres container. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(properties = ["spring.grpc.server.port=0", "spring.grpc.server.inprocess.name=$CATALOG_IN_PROCESS_NAME"])
@Import(TestcontainersConfiguration::class)
annotation class CatalogGrpcTest

fun badRequestFields(error: StatusRuntimeException): List<String> =
    StatusProto.fromThrowable(error)!!.detailsList
        .single { it.`is`(BadRequest::class.java) }
        .unpack(BadRequest::class.java)
        .fieldViolationsList.map { it.field }
