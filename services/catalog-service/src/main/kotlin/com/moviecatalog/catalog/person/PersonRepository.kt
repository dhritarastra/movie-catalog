package com.moviecatalog.catalog.person

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PersonRepository : JpaRepository<Person, UUID>
