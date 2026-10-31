package com.moviecatalog.gateway.tmdb

import com.moviecatalog.gateway.graphql.ErrorCode
import com.moviecatalog.gateway.graphql.GatewayException
import com.moviecatalog.gateway.grpc.CatalogClient
import com.moviecatalog.gateway.grpc.UploadSource
import com.moviecatalog.gateway.movie.ArtworkUploader
import com.moviecatalog.gateway.movie.MovieView
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class TmdbController(
    private val tmdb: TmdbClient,
    private val catalog: CatalogClient,
    private val uploader: ArtworkUploader,
) {
    @QueryMapping
    fun tmdbPostersEnabled(): Boolean = tmdb.enabled

    @QueryMapping
    fun tmdbPoster(@Argument movieId: String): TmdbPosterView? {
        val movie = catalog.getMovie(movieId)
        val year = if (movie.hasReleaseDate()) movie.releaseDate.take(4) else null
        return tmdb.findPoster(movie.title, year)?.let { TmdbPosterView(it.tmdbId, it.title, it.releaseYear, tmdb.previewUrl(it)) }
    }

    /** Downloads the poster and attaches it exactly like an uploaded file. */
    @MutationMapping
    fun useTmdbPoster(@Argument movieId: String, @Argument version: Int, @Argument tmdbId: Int): MovieView {
        val poster = tmdb.poster(tmdbId) ?: throw GatewayException(ErrorCode.NOT_FOUND, "TMDB has no poster for that film")
        val bytes = tmdb.download(poster)
        val source = UploadSource("image/jpeg", bytes.size.toLong(), "tmdb-$tmdbId.jpg") { bytes.inputStream() }
        return MovieView.of(uploader.upload(movieId, version, source))
    }
}
