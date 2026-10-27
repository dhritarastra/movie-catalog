package com.moviecatalog.gateway.person

/** The GraphQL `PersonPage`, shaped like MoviePageView. */
data class PersonPageView(val items: List<PersonView>, val totalCount: Int, val offset: Int, val limit: Int)
