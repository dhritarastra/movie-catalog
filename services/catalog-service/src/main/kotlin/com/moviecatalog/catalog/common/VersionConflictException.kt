package com.moviecatalog.catalog.common

/** The caller's expected version is not the stored one: someone else changed the record first. */
class VersionConflictException(message: String) : CatalogException(message)
