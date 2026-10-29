package com.moviecatalog.catalog.person

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PersonRepository : JpaRepository<Person, UUID> {
    /**
     * People whose name contains [name], ignoring case. Spring Data escapes % and _ in the
     * argument, so they are matched as themselves rather than as wildcards.
     */
    fun findAllByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Person>
}
