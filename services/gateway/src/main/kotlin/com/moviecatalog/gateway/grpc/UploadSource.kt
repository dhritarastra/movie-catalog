package com.moviecatalog.gateway.grpc

import java.io.InputStream

/** What the gateway knows about a file before streaming it; [open] is called once, when sending starts. */
data class UploadSource(val contentType: String, val sizeBytes: Long, val filename: String, val open: () -> InputStream)
