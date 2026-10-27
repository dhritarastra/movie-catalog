package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.common.RequestArguments
import com.moviecatalog.catalog.common.complete
import com.moviecatalog.catalog.common.toTimestamp
import com.moviecatalog.catalog.v1.ArtworkChangeResponse
import com.moviecatalog.catalog.v1.ClearArtworkRequest
import com.moviecatalog.catalog.v1.CreateMovieRequest
import com.moviecatalog.catalog.v1.DeleteMovieRequest
import com.moviecatalog.catalog.v1.DeleteMovieResponse
import com.moviecatalog.catalog.v1.GetMovieRequest
import com.moviecatalog.catalog.v1.ListMoviesRequest
import com.moviecatalog.catalog.v1.ListMoviesResponse
import com.moviecatalog.catalog.v1.MovieFields
import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.catalog.v1.SetArtworkRequest
import com.moviecatalog.catalog.v1.UpdateMovieRequest
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service
import com.moviecatalog.catalog.v1.Movie as MovieProto

/**
 * gRPC endpoint for movies: translates between protobuf messages and the domain, and leaves
 * business rules to [MovieService]. Exceptions are mapped to statuses by
 * CatalogGrpcExceptionHandler. Methods not overridden yet return UNIMPLEMENTED.
 */
@Service
class MovieGrpcService(private val movies: MovieService) : MovieServiceGrpc.MovieServiceImplBase() {
    override fun createMovie(request: CreateMovieRequest, responseObserver: StreamObserver<MovieProto>) =
        responseObserver.complete(movies.create(request.fields.toInput()).toProto())

    override fun getMovie(request: GetMovieRequest, responseObserver: StreamObserver<MovieProto>) =
        responseObserver.complete(movies.get(RequestArguments.uuid("id", request.id)).toProto())

    override fun listMovies(request: ListMoviesRequest, responseObserver: StreamObserver<ListMoviesResponse>) {
        val page = movies.list(request.page.offset, request.page.limit)
        responseObserver.complete(
            ListMoviesResponse.newBuilder()
                .addAllMovies(page.content.map { it.toProto() })
                .setTotalCount(page.totalElements.toInt())
                .build(),
        )
    }

    override fun updateMovie(request: UpdateMovieRequest, responseObserver: StreamObserver<MovieProto>) =
        responseObserver.complete(
            movies.update(
                id = RequestArguments.uuid("id", request.id),
                expectedVersion = RequestArguments.expectedVersion(request.expectedVersion),
                input = request.fields.toInput(),
            ).toProto(),
        )

    override fun deleteMovie(request: DeleteMovieRequest, responseObserver: StreamObserver<DeleteMovieResponse>) {
        val id = RequestArguments.uuid("id", request.id)
        val artworkId = movies.delete(id, RequestArguments.expectedVersion(request.expectedVersion))
        responseObserver.complete(
            DeleteMovieResponse.newBuilder()
                .setId(id.toString())
                .apply { artworkId?.let { setArtworkId(it.toString()) } }
                .build(),
        )
    }

    override fun setArtwork(request: SetArtworkRequest, responseObserver: StreamObserver<ArtworkChangeResponse>) =
        responseObserver.complete(
            movies.setArtwork(
                id = RequestArguments.uuid("movie_id", request.movieId),
                expectedVersion = RequestArguments.expectedVersion(request.expectedVersion),
                artworkId = RequestArguments.uuid("artwork_id", request.artworkId),
            ).toProto(),
        )

    override fun clearArtwork(request: ClearArtworkRequest, responseObserver: StreamObserver<ArtworkChangeResponse>) =
        responseObserver.complete(
            movies.clearArtwork(
                id = RequestArguments.uuid("movie_id", request.movieId),
                expectedVersion = RequestArguments.expectedVersion(request.expectedVersion),
            ).toProto(),
        )
}

// Inside the builder's apply, a bare `previousArtworkId` would be the builder's own field (""),
// so the receiver is named explicitly.
private fun ArtworkChange.toProto(): ArtworkChangeResponse =
    ArtworkChangeResponse.newBuilder()
        .setMovie(movie.toProto())
        .apply { this@toProto.previousArtworkId?.let { setPreviousArtworkId(it.toString()) } }
        .build()

private fun MovieFields.toInput() =
    MovieInput.normalised(
        title = title,
        synopsis = if (hasSynopsis()) synopsis else null,
        releaseDate = if (hasReleaseDate()) releaseDate else null,
        runtimeMinutes = if (hasRuntimeMinutes()) runtimeMinutes else null,
        genres = genresList,
    )

private fun Movie.toProto(): MovieProto =
    MovieProto.newBuilder()
        .setId(requireNotNull(id) { "a saved movie has an id" }.toString())
        .setTitle(title)
        .addAllGenres(genres)
        .setVersion(version)
        .setCreatedAt(createdAt.toTimestamp())
        .setUpdatedAt(updatedAt.toTimestamp())
        .apply {
            this@toProto.synopsis?.let { setSynopsis(it) }
            this@toProto.releaseDate?.let { setReleaseDate(it.toString()) }
            this@toProto.runtimeMinutes?.let { setRuntimeMinutes(it) }
            this@toProto.artworkId?.let { setArtworkId(it.toString()) }
        }
        .build()
