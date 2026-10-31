package com.moviecatalog.gateway.graphql

import com.moviecatalog.gateway.config.GatewayProperties
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

/** The servlet container rejects oversized multipart requests before any handler runs. */
@RestControllerAdvice
class UploadLimitAdvice(private val properties: GatewayProperties) {
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(GraphQlMultipartHandler.errorBody(ErrorCode.RESOURCE_EXHAUSTED, "The image must be at most ${properties.maxUploadBytes / (1024 * 1024)} MB"))
}
