package com.moviecatalog.gateway.search

import com.moviecatalog.gateway.movie.MovieView
import com.moviecatalog.gateway.person.PersonView
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer

/** Maps MovieView and PersonView to the schema's Movie and Person types in the SearchResult union. */
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
