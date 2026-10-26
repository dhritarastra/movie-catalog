package com.moviecatalog.catalog.common

/** One problem with one request field, reported to callers as a google.rpc.BadRequest violation. */
data class FieldViolation(val field: String, val description: String)

/**
 * Business failures the catalog reports. [CatalogGrpcExceptionHandler] maps each one to a gRPC
 * status, so service code throws these instead of building statuses itself.
 */
sealed class CatalogException(message: String) : RuntimeException(message)

class NotFoundException(message: String) : CatalogException(message)

class InvalidArgumentException(val violations: List<FieldViolation>) :
    CatalogException(violations.joinToString("; ") { "${it.field}: ${it.description}" })

/** The caller's expected version is not the stored one: someone else changed the record first. */
class VersionConflictException(message: String) : CatalogException(message)
