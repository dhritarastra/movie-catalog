package com.moviecatalog.catalog.common

/** The request would create a second copy of something that must be unique. */
class AlreadyExistsException(message: String) : CatalogException(message)
