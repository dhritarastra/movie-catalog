package com.moviecatalog.catalog.common

/** The request broke one or more field rules; every problem is reported at once. */
class InvalidArgumentException(val violations: List<FieldViolation>) :
    CatalogException(violations.joinToString("; ") { "${it.field}: ${it.description}" })
