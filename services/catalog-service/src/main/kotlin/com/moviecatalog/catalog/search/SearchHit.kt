package com.moviecatalog.catalog.search

import com.moviecatalog.catalog.movie.Movie
import com.moviecatalog.catalog.person.Person

/** One search result: a movie or a person, in the order SearchRepository ranked them. */
sealed interface SearchHit {
    data class OfMovie(val movie: Movie) : SearchHit

    data class OfPerson(val person: Person) : SearchHit
}
