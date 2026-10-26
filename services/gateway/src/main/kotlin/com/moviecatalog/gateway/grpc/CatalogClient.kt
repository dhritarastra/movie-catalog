package com.moviecatalog.gateway.grpc

import com.moviecatalog.catalog.v1.ArtworkChangeResponse
import com.moviecatalog.catalog.v1.CreateMovieRequest
import com.moviecatalog.catalog.v1.GetMovieRequest
import com.moviecatalog.catalog.v1.ListMoviesRequest
import com.moviecatalog.catalog.v1.ListMoviesResponse
import com.moviecatalog.catalog.v1.Movie
import com.moviecatalog.catalog.v1.MovieFields
import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.catalog.v1.PageRequest
import com.moviecatalog.catalog.v1.SetArtworkRequest
import com.moviecatalog.gateway.GatewayProperties
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * The catalog calls the gateway makes, each with a deadline. Failures surface as
 * StatusRuntimeException and are turned into GraphQL errors by GrpcErrorResolver.
 */
@Component
class CatalogClient(
    private val movies: MovieServiceGrpc.MovieServiceBlockingStub,
    private val properties: GatewayProperties,
) {
    fun listMovies(offset: Int, limit: Int): ListMoviesResponse =
        stub().listMovies(
            ListMoviesRequest.newBuilder().setPage(PageRequest.newBuilder().setOffset(offset).setLimit(limit)).build(),
        )

    fun getMovie(id: String): Movie = stub().getMovie(GetMovieRequest.newBuilder().setId(id).build())

    fun createMovie(fields: MovieFields): Movie = stub().createMovie(CreateMovieRequest.newBuilder().setFields(fields).build())

    fun setArtwork(movieId: String, expectedVersion: Int, artworkId: String): ArtworkChangeResponse =
        stub().setArtwork(
            SetArtworkRequest.newBuilder()
                .setMovieId(movieId)
                .setExpectedVersion(expectedVersion)
                .setArtworkId(artworkId)
                .build(),
        )

    private fun stub() = movies.withDeadlineAfter(properties.catalogDeadline.toMillis(), TimeUnit.MILLISECONDS)
}
