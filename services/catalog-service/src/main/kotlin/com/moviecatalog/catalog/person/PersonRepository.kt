package com.moviecatalog.catalog.person

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PersonRepository : JpaRepository<Person, UUID> {
    /** Spring Data escapes % and _ in [name], so they match literally. */
    fun findAllByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Person>
}
