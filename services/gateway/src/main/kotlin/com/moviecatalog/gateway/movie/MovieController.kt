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
    fun createMovie(@Argument input: MovieInput): MovieView =
        MovieView.of(
            catalog.createMovie(
                MovieFields.newBuilder()
                    .setTitle(input.title)
                    .addAllGenres(input.genres)
                    .apply {
                        input.synopsis?.let { setSynopsis(it) }
                        input.releaseDate?.let { setReleaseDate(it) }
                        input.runtimeMinutes?.let { setRuntimeMinutes(it) }
                    }
                    .build(),
            ),
        )

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
