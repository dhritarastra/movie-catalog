package com.moviecatalog.gateway.search

import com.moviecatalog.catalog.v1.SearchHit
import com.moviecatalog.gateway.grpc.CatalogClient
import com.moviecatalog.gateway.movie.MovieView
import com.moviecatalog.gateway.person.PersonView
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

/**
 * One search across movie titles and person names, in the catalog's ranked order. Each hit is
 * a MovieView or a PersonView, so the movie and person fields (artworkUrl, credits, ...) resolve
 * exactly as they do elsewhere; SearchResultTypeConfiguration tells GraphQL which is which.
 */
@Controller
class SearchController(private val catalog: CatalogClient) {
    @QueryMapping
    fun search(@Argument query: String, @Argument limit: Int): List<Any> =
        catalog.search(query, limit).map { hit ->
            when (hit.hitCase) {
                SearchHit.HitCase.MOVIE -> MovieView.of(hit.movie)
                SearchHit.HitCase.PERSON -> PersonView.of(hit.person)
                // The catalog always sets one of the two.
                SearchHit.HitCase.HIT_NOT_SET, null -> error("A search hit from the catalog is neither a movie nor a person")
            }
        }
}
