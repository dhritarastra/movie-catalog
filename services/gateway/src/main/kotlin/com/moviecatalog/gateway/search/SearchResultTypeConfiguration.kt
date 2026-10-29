package com.moviecatalog.gateway.search

import com.moviecatalog.gateway.movie.MovieView
import com.moviecatalog.gateway.person.PersonView
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer

/**
 * Tells GraphQL which member of the `SearchResult` union each hit is. Spring for GraphQL would
 * otherwise look for types named after the classes (MovieView, PersonView), which the schema
 * calls Movie and Person.
 */
@Configuration
class SearchResultTypeConfiguration {
    @Bean
    fun searchResultTypes(): RuntimeWiringConfigurer =
        RuntimeWiringConfigurer { wiring ->
            wiring.type("SearchResult") { type ->
                type.typeResolver { env ->
                    val name = when (env.getObject<Any>()) {
                        is MovieView -> "Movie"
                        is PersonView -> "Person"
                        else -> error("Unexpected search result ${env.getObject<Any>()::class.simpleName}")
                    }
                    env.schema.getObjectType(name)
                }
            }
        }
}
