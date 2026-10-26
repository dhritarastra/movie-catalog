package com.moviecatalog.catalog.movie

import com.google.protobuf.Timestamp
import com.moviecatalog.catalog.common.RequestArguments
import com.moviecatalog.catalog.v1.ArtworkChangeResponse
import com.moviecatalog.catalog.v1.CreateMovieRequest
import com.moviecatalog.catalog.v1.GetMovieRequest
import com.moviecatalog.catalog.v1.ListMoviesRequest
import com.moviecatalog.catalog.v1.ListMoviesResponse
import com.moviecatalog.catalog.v1.MovieFields
import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.catalog.v1.SetArtworkRequest
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service
import java.time.Instant
import com.moviecatalog.catalog.v1.Movie as MovieProto

/**
 * gRPC endpoint for movies: translates between protobuf messages and the domain, and leaves
 * business rules to [MovieService]. Exceptions are mapped to statuses by
 * CatalogGrpcExceptionHandler. Methods not overridden yet return UNIMPLEMENTED; they are
 * filled in slice by slice (docs/PLAN.md §11).
 */
@Service
class MovieGrpcService(private val movies: MovieService) : MovieServiceGrpc.MovieServiceImplBase() {
    override fun createMovie(request: CreateMovieRequest, responseObserver: StreamObserver<MovieProto>) =
        responseObserver.complete(movies.create(request.fields.toInput()).toProto())

    override fun getMovie(request: GetMovieRequest, responseObserver: StreamObserver<MovieProto>) =
        responseObserver.complete(movies.get(RequestArguments.uuid("id", request.id)).toProto())

    override fun listMovies(request: ListMoviesRequest, responseObserver: StreamObserver<ListMoviesResponse>) {
        val page = movies.list(RequestArguments.page(request.page.offset, request.page.limit))
        responseObserver.complete(
            ListMoviesResponse.newBuilder()
                .addAllMovies(page.movies.map { it.toProto() })
                .setTotalCount(page.totalCount)
                .build(),
        )
    }

    override fun setArtwork(request: SetArtworkRequest, responseObserver: StreamObserver<ArtworkChangeResponse>) {
        val change = movies.setArtwork(
            id = RequestArguments.uuid("movie_id", request.movieId),
            expectedVersion = RequestArguments.expectedVersion(request.expectedVersion),
            artworkId = RequestArguments.uuid("artwork_id", request.artworkId),
        )
        responseObserver.complete(
            ArtworkChangeResponse.newBuilder()
                .setMovie(change.movie.toProto())
                .apply { change.previousArtworkId?.let { setPreviousArtworkId(it.toString()) } }
                .build(),
        )
    }
}

private fun <T> StreamObserver<T>.complete(value: T) {
    onNext(value)
    onCompleted()
}

private fun MovieFields.toInput() =
    MovieInput(
        title = title,
        synopsis = if (hasSynopsis()) synopsis else null,
        releaseDate = if (hasReleaseDate()) releaseDate else null,
        runtimeMinutes = if (hasRuntimeMinutes()) runtimeMinutes else null,
        genres = genresList,
    )

private fun Movie.toProto(): MovieProto =
    MovieProto.newBuilder()
        .setId(id.toString())
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

private fun Instant.toTimestamp(): Timestamp = Timestamp.newBuilder().setSeconds(epochSecond).setNanos(nano).build()
