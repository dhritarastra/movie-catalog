package com.moviecatalog.gateway.movie

import com.moviecatalog.catalog.v1.MovieFields
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

enum class ArtworkVariant { FULL, THUMBNAIL }

@Controller
class MovieController(
    private val catalog: CatalogClient,
    private val uploader: ArtworkUploader,
    private val cleaner: ArtworkCleaner,
) {
    @QueryMapping
    fun movies(@Argument page: PageInput?): MoviePageView {
        val offset = page?.offset ?: 0
        val limit = page?.limit ?: DEFAULT_PAGE_SIZE
        val response = catalog.listMovies(offset, limit)
        return MoviePageView(response.moviesList.map(MovieView::of), response.totalCount, offset, limit)
    }

    /** A missing movie is `null`, as the schema allows; a malformed ID is still an error. */
    @QueryMapping
    fun movie(@Argument id: String): MovieView? =
        try {
            MovieView.of(catalog.getMovie(id))
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.NOT_FOUND) null else throw e
        }

    @MutationMapping
    fun createMovie(@Argument input: MovieInput): MovieView = MovieView.of(catalog.createMovie(input.toFields()))

    /** Replaces every editable field; fails with CONFLICT if the movie changed since [version] was read. */
    @MutationMapping
    fun updateMovie(@Argument id: String, @Argument version: Int, @Argument input: MovieInput): MovieView =
        MovieView.of(catalog.updateMovie(id, version, input.toFields()))

    /**
     * Deletes the movie, then its artwork file. The file is deleted only after the catalog has
     * confirmed, so a failed or uncertain delete never leaves a movie pointing at a missing image.
     */
    @MutationMapping
    fun deleteMovie(@Argument id: String, @Argument version: Int): String {
        val deleted = catalog.deleteMovie(id, version)
        if (deleted.hasArtworkId()) cleaner.deleteQuietly(deleted.artworkId, "movie $id was deleted")
        return deleted.id
    }

    /** Detaches the artwork (with the same version check), then deletes the file it pointed to. */
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

    /** Image URLs include the artwork ID, so a new upload gets a new URL and old ones can be cached forever. */
    @SchemaMapping(typeName = "Movie", field = "artworkUrl")
    fun artworkUrl(movie: MovieView, @Argument variant: ArtworkVariant): String? =
        movie.artworkId?.let { "/media/$it?variant=${if (variant == ArtworkVariant.THUMBNAIL) "thumb" else "full"}" }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

private fun MovieInput.toFields(): MovieFields =
    MovieFields.newBuilder()
        .setTitle(title)
        .addAllGenres(genres)
        // Named explicitly: inside the builder's apply, a bare `synopsis` would be the builder's field.
        .apply {
            this@toFields.synopsis?.let { setSynopsis(it) }
            this@toFields.releaseDate?.let { setReleaseDate(it) }
            this@toFields.runtimeMinutes?.let { setRuntimeMinutes(it) }
        }
        .build()
