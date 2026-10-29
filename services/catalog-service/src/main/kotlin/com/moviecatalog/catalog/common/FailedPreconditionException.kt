package com.moviecatalog.catalog.common

/** The request is valid, but not allowed until something else changes first; the message says what. */
class FailedPreconditionException(message: String) : CatalogException(message)
