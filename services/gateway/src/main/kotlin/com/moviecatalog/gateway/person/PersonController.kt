package com.moviecatalog.gateway.person

import com.moviecatalog.catalog.v1.PersonFields
import com.moviecatalog.gateway.graphql.PageInput
import com.moviecatalog.gateway.graphql.limitOrDefault
import com.moviecatalog.gateway.graphql.offsetOrDefault
import com.moviecatalog.gateway.grpc.CatalogClient
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

/** People: cast members and creators. Same rules as movies: full-replacement updates guarded by `version`. */
@Controller
class PersonController(private val catalog: CatalogClient) {
    @QueryMapping
    fun people(@Argument page: PageInput?): PersonPageView {
        val offset = page.offsetOrDefault()
        val limit = page.limitOrDefault()
        val response = catalog.listPeople(offset, limit)
        return PersonPageView(response.peopleList.map(PersonView::of), response.totalCount, offset, limit)
    }

    /** A missing person is `null`, as the schema allows; a malformed ID is still an error. */
    @QueryMapping
    fun person(@Argument id: String): PersonView? =
        try {
            PersonView.of(catalog.getPerson(id))
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.NOT_FOUND) null else throw e
        }

    @MutationMapping
    fun createPerson(@Argument input: PersonInput): PersonView = PersonView.of(catalog.createPerson(input.toFields()))

    /** Replaces every editable field; fails with CONFLICT if the person changed since [version] was read. */
    @MutationMapping
    fun updatePerson(@Argument id: String, @Argument version: Int, @Argument input: PersonInput): PersonView =
        PersonView.of(catalog.updatePerson(id, version, input.toFields()))

    @MutationMapping
    fun deletePerson(@Argument id: String, @Argument version: Int): String = catalog.deletePerson(id, version)
}

private fun PersonInput.toFields(): PersonFields =
    PersonFields.newBuilder()
        .setName(name)
        // Named explicitly: inside the builder's apply, a bare `bio` would be the builder's field.
        .apply {
            this@toFields.bio?.let { setBio(it) }
            this@toFields.birthDate?.let { setBirthDate(it) }
        }
        .build()
