package com.moviecatalog.gateway

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("gateway")
data class GatewayProperties(
    val maxUploadBytes: Long,
    /** Deadline for every catalog call; a hung catalog fails the request instead of hanging it. */
    val catalogDeadline: Duration,
    val mediaUploadDeadline: Duration,
    val mediaDownloadDeadline: Duration,
)
