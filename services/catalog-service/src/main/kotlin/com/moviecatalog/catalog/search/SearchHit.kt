package com.moviecatalog.catalog.search

import com.moviecatalog.catalog.movie.Movie
import com.moviecatalog.catalog.person.Person

sealed interface SearchHit {
    data class OfMovie(val movie: Movie) : SearchHit

    data class OfPerson(val person: Person) : SearchHit
}
