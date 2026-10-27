package com.moviecatalog.gateway.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

/** `gateway.*` in application.yml. */
@Validated
@ConfigurationProperties("gateway")
data class GatewayProperties(
    /** Largest accepted upload, checked before anything is sent to media. */
    val maxUploadBytes: Long,
    /** Deadline for every catalog call; a hung catalog fails the request instead of hanging it. */
    val catalogDeadline: Duration,
    val mediaUploadDeadline: Duration,
    val mediaDownloadDeadline: Duration,
    /** Size of each chunk an upload is streamed to media in; gRPC's default message limit is 4 MiB. */
    @field:Min(1)
    @field:Max(1_048_576) // Leaves ample room for protobuf framing below the 4 MiB gRPC limit.
    val mediaUploadChunkBytes: Int,
    /** How long a multipart GraphQL request may run before the gateway gives up waiting for it. */
    val multipartTimeout: Duration,
)
