package com.moviecatalog.catalog.common

/**
 * Business failures the catalog reports. [CatalogGrpcExceptionHandler] maps each one to a gRPC
 * status, so service code throws these instead of building statuses itself.
 */
sealed class CatalogException(message: String) : RuntimeException(message)
