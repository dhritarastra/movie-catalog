package com.moviecatalog.catalog.search

import java.util.UUID

/** One ranked match from SearchRepository: which kind of record, and its ID. */
data class SearchMatch(val kind: SearchKind, val id: UUID)
