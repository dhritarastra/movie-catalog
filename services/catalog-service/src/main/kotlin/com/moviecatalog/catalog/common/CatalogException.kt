package com.moviecatalog.catalog.common

/** Business errors. CatalogGrpcExceptionHandler maps them to gRPC statuses. */
sealed class CatalogException(message: String) : RuntimeException(message)
