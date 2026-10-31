package com.moviecatalog.gateway.person

data class PersonPageView(val items: List<PersonView>, val totalCount: Int, val offset: Int, val limit: Int)
