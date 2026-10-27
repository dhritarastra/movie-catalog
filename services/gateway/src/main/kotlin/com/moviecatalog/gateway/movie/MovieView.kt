package com.moviecatalog.gateway.movie

import com.moviecatalog.gateway.graphql.toIsoString
import com.moviecatalog.catalog.v1.Movie as MovieProto

/**
 * The GraphQL `Movie` as data. Protobuf getters return "" or 0 for unset optional fields,
 * so this view turns them back into nulls before GraphQL sees them.
 */
data class MovieView(
    val id: String,
    val title: String,
    val synopsis: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val artworkId: String?,
    val version: Int,
    val createdAt: String,
    val updatedAt: String,
) {
    companion object {
        fun of(movie: MovieProto) =
            MovieView(
                id = movie.id,
                title = movie.title,
                synopsis = if (movie.hasSynopsis()) movie.synopsis else null,
                releaseDate = if (movie.hasReleaseDate()) movie.releaseDate else null,
                runtimeMinutes = if (movie.hasRuntimeMinutes()) movie.runtimeMinutes else null,
                genres = movie.genresList,
                artworkId = if (movie.hasArtworkId()) movie.artworkId else null,
                version = movie.version,
                createdAt = movie.createdAt.toIsoString(),
                updatedAt = movie.updatedAt.toIsoString(),
            )
    }
}
