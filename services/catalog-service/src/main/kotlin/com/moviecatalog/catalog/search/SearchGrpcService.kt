package com.moviecatalog.catalog.search

import com.moviecatalog.catalog.common.complete
import com.moviecatalog.catalog.movie.toProto
import com.moviecatalog.catalog.person.toProto
import com.moviecatalog.catalog.v1.SearchRequest
import com.moviecatalog.catalog.v1.SearchResponse
import com.moviecatalog.catalog.v1.SearchServiceGrpc
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service
import com.moviecatalog.catalog.v1.SearchHit as SearchHitProto

/** gRPC endpoint for search, shaped like MovieGrpcService. */
@Service
class SearchGrpcService(private val search: SearchService) : SearchServiceGrpc.SearchServiceImplBase() {
    override fun search(request: SearchRequest, responseObserver: StreamObserver<SearchResponse>) =
        responseObserver.complete(
            SearchResponse.newBuilder().addAllHits(search.search(request.query, request.limit).map { it.toProto() }).build(),
        )
}

private fun SearchHit.toProto(): SearchHitProto =
    when (this) {
        is SearchHit.OfMovie -> SearchHitProto.newBuilder().setMovie(movie.toProto()).build()
        is SearchHit.OfPerson -> SearchHitProto.newBuilder().setPerson(person.toProto()).build()
    }
