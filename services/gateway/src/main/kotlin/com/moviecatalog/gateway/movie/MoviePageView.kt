package com.moviecatalog.gateway.movie

/** The GraphQL `MoviePage`: one page of movies, the total across all pages, and the page asked for. */
data class MoviePageView(val items: List<MovieView>, val totalCount: Int, val offset: Int, val limit: Int)
