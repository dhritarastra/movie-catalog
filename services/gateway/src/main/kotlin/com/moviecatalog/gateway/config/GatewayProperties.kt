package com.moviecatalog.gateway.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties("gateway")
data class GatewayProperties(
    val maxUploadBytes: Long,
    val catalogDeadline: Duration,
    val mediaUploadDeadline: Duration,
    val mediaDownloadDeadline: Duration,
    // Well under gRPC's 4 MiB message limit.
    @field:Min(1)
    @field:Max(1_048_576)
    val mediaUploadChunkBytes: Int,
    val multipartTimeout: Duration,
)
