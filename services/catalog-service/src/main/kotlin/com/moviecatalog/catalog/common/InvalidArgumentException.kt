package com.moviecatalog.catalog.common

class InvalidArgumentException(val violations: List<FieldViolation>) :
    CatalogException(violations.joinToString("; ") { "${it.field}: ${it.description}" })
