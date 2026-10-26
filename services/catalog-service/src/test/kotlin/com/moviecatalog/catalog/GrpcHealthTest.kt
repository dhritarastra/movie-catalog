package com.moviecatalog.catalog

import io.grpc.ManagedChannel
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse.ServingStatus
import io.grpc.health.v1.HealthGrpc
import io.grpc.inprocess.InProcessChannelBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Duration

/**
 * Docker checks catalog health through the standard gRPC health service, so this verifies
 * that the gRPC status follows application health, including the database connection.
 */
@SpringBootTest(
    properties = [
        "spring.grpc.server.port=0",
        "spring.grpc.server.inprocess.name=$IN_PROCESS_NAME",
        "spring.grpc.server.health.schedule.delay=0s",
        "spring.grpc.server.health.schedule.period=1s",
        "spring.datasource.hikari.connection-timeout=1000",
    ],
)
@Testcontainers
class GrpcHealthTest {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    private val channel: ManagedChannel = InProcessChannelBuilder.forName(IN_PROCESS_NAME).directExecutor().build()
    private val health = HealthGrpc.newBlockingStub(channel)

    @AfterEach
    fun closeChannel() {
        channel.shutdownNow()
    }

    @Test
    fun `reports SERVING while the database is up and NOT_SERVING once it is down`() {
        awaitStatus(ServingStatus.SERVING)

        postgres.stop()

        awaitStatus(ServingStatus.NOT_SERVING)
    }

    private fun awaitStatus(expected: ServingStatus, timeout: Duration = Duration.ofSeconds(20)) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var last: ServingStatus? = null
        while (System.nanoTime() < deadline) {
            last = health.check(HealthCheckRequest.getDefaultInstance()).status
            if (last == expected) return
            Thread.sleep(250)
        }
        assertThat(last).describedAs("gRPC health status after %s", timeout).isEqualTo(expected)
    }
}

private const val IN_PROCESS_NAME = "catalog-health-test"
