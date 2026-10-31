package com.moviecatalog.gateway.grpc

import java.io.InputStream

/** [open] is called once, when sending starts. */
data class UploadSource(val contentType: String, val sizeBytes: Long, val filename: String, val open: () -> InputStream)
