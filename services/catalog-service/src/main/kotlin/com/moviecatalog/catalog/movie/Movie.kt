package com.moviecatalog.catalog.movie

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Movie(
    val id: UUID,
    val title: String,
    val synopsis: String?,
    val releaseDate: LocalDate?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val artworkId: UUID?,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Editable fields as received, before validation. */
data class MovieInput(
    val title: String,
    val synopsis: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
)

/** Editable fields after [MovieValidator] has normalised and checked them. */
data class MovieDraft(
    val title: String,
    val synopsis: String?,
    val releaseDate: LocalDate?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
)

data class MoviePage(val movies: List<Movie>, val totalCount: Int)

/** Result of attaching or removing artwork: the updated movie and the artwork it replaced, if any. */
data class ArtworkChange(val movie: Movie, val previousArtworkId: UUID?)

/** A deleted movie's ID and the artwork it had, which the gateway then deletes from media. */
data class DeletedMovie(val id: UUID, val artworkId: UUID?)
