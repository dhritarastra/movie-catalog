package com.moviecatalog.gateway.search

import com.moviecatalog.catalog.v1.SearchHit
import com.moviecatalog.gateway.grpc.CatalogClient
import com.moviecatalog.gateway.movie.MovieView
import com.moviecatalog.gateway.person.PersonView
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class SearchController(private val catalog: CatalogClient) {
    /** A null limit means the default. */
    @QueryMapping
    fun search(@Argument query: String, @Argument limit: Int?): List<Any> =
        catalog.search(query, limit ?: DEFAULT_SEARCH_LIMIT).map { hit ->
            when (hit.hitCase) {
                SearchHit.HitCase.MOVIE -> MovieView.of(hit.movie)
                SearchHit.HitCase.PERSON -> PersonView.of(hit.person)
                SearchHit.HitCase.HIT_NOT_SET, null -> error("A search hit from the catalog is neither a movie nor a person")
            }
        }
}

// Matches the default in schema.graphqls.
const val DEFAULT_SEARCH_LIMIT = 10
