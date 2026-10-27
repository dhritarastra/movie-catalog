package com.moviecatalog.gateway.person

import com.moviecatalog.gateway.graphql.toIsoString
import com.moviecatalog.catalog.v1.Person as PersonProto

/** The GraphQL `Person` as data, with unset optional protobuf fields turned back into nulls. */
data class PersonView(
    val id: String,
    val name: String,
    val bio: String?,
    val birthDate: String?,
    val version: Int,
    val createdAt: String,
    val updatedAt: String,
) {
    companion object {
        fun of(person: PersonProto) =
            PersonView(
                id = person.id,
                name = person.name,
                bio = if (person.hasBio()) person.bio else null,
                birthDate = if (person.hasBirthDate()) person.birthDate else null,
                version = person.version,
                createdAt = person.createdAt.toIsoString(),
                updatedAt = person.updatedAt.toIsoString(),
            )
    }
}
