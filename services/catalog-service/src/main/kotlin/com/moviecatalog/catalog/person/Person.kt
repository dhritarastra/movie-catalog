package com.moviecatalog.catalog.person

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A cast member or creator. Roles belong to credits, so the same person can be both. */
data class Person(
    val id: UUID,
    val name: String,
    val bio: String?,
    val birthDate: LocalDate?,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Editable fields as received, before validation. */
data class PersonInput(
    val name: String,
    val bio: String?,
    val birthDate: String?,
)

/** Editable fields after [PersonValidator] has normalised and checked them. */
data class PersonDraft(
    val name: String,
    val bio: String?,
    val birthDate: LocalDate?,
)

data class PersonPage(val people: List<Person>, val totalCount: Int)
