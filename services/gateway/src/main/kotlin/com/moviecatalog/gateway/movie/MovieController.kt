package com.moviecatalog.gateway.movie

import com.moviecatalog.catalog.v1.MovieFields
import com.moviecatalog.gateway.graphql.PageInput
import com.moviecatalog.gateway.graphql.limitOrDefault
import com.moviecatalog.gateway.graphql.offsetOrDefault
import com.moviecatalog.gateway.grpc.CatalogClient
import com.moviecatalog.gateway.grpc.UploadSource
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import org.springframework.web.multipart.MultipartFile

@Controller
class MovieController(
    private val catalog: CatalogClient,
    private val uploader: ArtworkUploader,
    private val cleaner: ArtworkCleaner,
) {
    @QueryMapping
    fun movies(@Argument page: PageInput?): MoviePageView {
        val offset = page.offsetOrDefault()
        val limit = page.limitOrDefault()
        val response = catalog.listMovies(offset, limit)
        return MoviePageView(response.moviesList.map(MovieView::of), response.totalCount, offset, limit)
    }

    /** A missing movie is null; a malformed ID is still an error. */
    @QueryMapping
    fun movie(@Argument id: String): MovieView? =
        try {
            MovieView.of(catalog.getMovie(id))
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.NOT_FOUND) null else throw e
        }

    @MutationMapping
    fun createMovie(@Argument input: MovieInput): MovieView = MovieView.of(catalog.createMovie(input.toFields()))

    @MutationMapping
    fun updateMovie(@Argument id: String, @Argument version: Int, @Argument input: MovieInput): MovieView =
        MovieView.of(catalog.updateMovie(id, version, input.toFields()))

    /** The file is deleted only after the catalog confirms the movie is gone. */
    @MutationMapping
    fun deleteMovie(@Argument id: String, @Argument version: Int): String {
        val deleted = catalog.deleteMovie(id, version)
        if (deleted.hasArtworkId()) cleaner.deleteQuietly(deleted.artworkId, "movie $id was deleted")
        return deleted.id
    }

    @MutationMapping
    fun removeArtwork(@Argument movieId: String, @Argument version: Int): MovieView {
        val change = catalog.clearArtwork(movieId, version)
        if (change.hasPreviousArtworkId()) cleaner.deleteQuietly(change.previousArtworkId, "it was removed from movie $movieId")
        return MovieView.of(change.movie)
    }

    @MutationMapping
    fun uploadArtwork(@Argument movieId: String, @Argument version: Int, @Argument file: MultipartFile): MovieView =
        MovieView.of(
            uploader.upload(
                movieId,
                version,
                UploadSource(file.contentType ?: "application/octet-stream", file.size, file.originalFilename ?: "", file::getInputStream),
            ),
        )

    /** Each upload has a new ID, so old URLs can be cached forever. */
    @SchemaMapping(typeName = "Movie", field = "artworkUrl")
    fun artworkUrl(movie: MovieView, @Argument variant: ArtworkVariant): String? =
        movie.artworkId?.let { "/media/$it?variant=${if (variant == ArtworkVariant.THUMBNAIL) "thumb" else "full"}" }
}

private fun MovieInput.toFields(): MovieFields =
    MovieFields.newBuilder()
        .setTitle(title)
        .addAllGenres(genres)
        // Inside apply, a bare synopsis would be the builder's own field.
        .apply {
            this@toFields.synopsis?.let { setSynopsis(it) }
            this@toFields.releaseDate?.let { setReleaseDate(it) }
            this@toFields.runtimeMinutes?.let { setRuntimeMinutes(it) }
        }
        .build()
