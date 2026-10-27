package com.moviecatalog.gateway.movie

/** `MovieInput` as received; the catalog validates and normalises it. */
data class MovieInput(
    val title: String,
    val synopsis: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
)
