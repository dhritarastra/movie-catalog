package com.moviecatalog.gateway.movie

data class MoviePageView(val items: List<MovieView>, val totalCount: Int, val offset: Int, val limit: Int)
