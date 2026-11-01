package com.moviecatalog.catalog.search

import com.moviecatalog.catalog.common.FieldViolation
import com.moviecatalog.catalog.common.InvalidArgumentException
import com.moviecatalog.catalog.config.SearchProperties
import com.moviecatalog.catalog.movie.MovieInput
import com.moviecatalog.catalog.movie.MovieRepository
import com.moviecatalog.catalog.person.PersonInput
import com.moviecatalog.catalog.person.PersonRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SearchService(
    private val search: SearchRepository,
    private val movies: MovieRepository,
    private val people: PersonRepository,
    private val properties: SearchProperties,
) {
    fun search(query: String, limit: Int): List<SearchHit> {
        val text = query.trim()
        val violations = buildList {
            if (text.isEmpty()) add(FieldViolation("query", "is required"))
            if (text.length > QUERY_MAX) add(FieldViolation("query", "must be at most $QUERY_MAX characters"))
            if (limit !in 1..properties.maxLimit) add(FieldViolation("limit", "must be between 1 and ${properties.maxLimit}"))
        }
        if (violations.isNotEmpty()) throw InvalidArgumentException(violations)

        val matches = search.search(text, limit)
        val moviesById = movies.findAllById(matches.idsOf(SearchKind.MOVIE)).associateBy { it.id }
        val peopleById = people.findAllById(matches.idsOf(SearchKind.PERSON)).associateBy { it.id }
        // Keeps rank order; anything deleted in between is skipped.
        return matches.mapNotNull { match ->
            when (match.kind) {
                SearchKind.MOVIE -> moviesById[match.id]?.let { SearchHit.OfMovie(it) }
                SearchKind.PERSON -> peopleById[match.id]?.let { SearchHit.OfPerson(it) }
            }
        }
    }

    private fun List<SearchMatch>.idsOf(kind: SearchKind) = filter { it.kind == kind }.map { it.id }

    private companion object {
        // A longer query cannot match any title or name.
        val QUERY_MAX = maxOf(MovieInput.TITLE_MAX, PersonInput.NAME_MAX)
    }
}
