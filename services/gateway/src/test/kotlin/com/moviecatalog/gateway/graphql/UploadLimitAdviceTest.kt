package com.moviecatalog.gateway.graphql

import com.moviecatalog.gateway.config.GatewayProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Duration

class UploadLimitAdviceTest {
    @Test
    fun `an upload over the servlet limit is a 413 with RESOURCE_EXHAUSTED and the configured limit`() {
        val properties = GatewayProperties(5_242_880, Duration.ofSeconds(2), Duration.ofSeconds(30), Duration.ofSeconds(30), 64 * 1024, Duration.ofSeconds(60))

        val response = UploadLimitAdvice(properties).tooLarge()

        assertThat(response.statusCode).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
        assertThat(response.body).isEqualTo(
            mapOf("errors" to listOf(mapOf("message" to "The image must be at most 5 MB", "extensions" to mapOf("code" to "RESOURCE_EXHAUSTED")))),
        )
    }
}
