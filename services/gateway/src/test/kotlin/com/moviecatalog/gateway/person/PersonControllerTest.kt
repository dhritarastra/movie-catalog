package com.moviecatalog.gateway.person

import com.google.protobuf.Any
import com.google.rpc.BadRequest
import com.google.rpc.Code
import com.moviecatalog.catalog.v1.ListPeopleResponse
import com.moviecatalog.catalog.v1.Person
import com.moviecatalog.catalog.v1.PersonFields
import com.moviecatalog.gateway.grpc.CatalogClient
import com.ninjasquad.springmockk.MockkBean
import io.grpc.Status
import io.grpc.protobuf.StatusProto
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester

/** The person queries and mutations with the catalog mocked: argument mapping, nulls and error codes. */
@SpringBootTest
class PersonControllerTest(@Autowired graphQlService: ExecutionGraphQlService) {
    @MockkBean
    private lateinit var catalog: CatalogClient

    private val graphQl = ExecutionGraphQlServiceTester.create(graphQlService)

    @Test
    fun `people pages with the default size and turns unset optional fields into nulls`() {
        every { catalog.listPeople(0, 20) } returns
            ListPeopleResponse.newBuilder()
                .addPeople(Person.newBuilder().setId(PERSON).setName("Amy Adams").setBirthDate("1974-08-20").setVersion(1))
                .setTotalCount(1)
                .build()

        graphQl.document("{ people { totalCount offset limit items { id name bio birthDate version } } }")
            .execute()
            .path("people.totalCount").entity(Int::class.java).isEqualTo(1)
            .path("people.limit").entity(Int::class.java).isEqualTo(20)
            .path("people.items[0].name").entity(String::class.java).isEqualTo("Amy Adams")
            .path("people.items[0].bio").valueIsNull()
            .path("people.items[0].birthDate").entity(String::class.java).isEqualTo("1974-08-20")
    }

    @Test
    fun `passes a name filter through, and sends none when it is omitted`() {
        every { catalog.listPeople(0, 20, "amy") } returns ListPeopleResponse.newBuilder().setTotalCount(0).build()

        graphQl.document("""{ people(nameContains: "amy") { totalCount } }""")
            .execute()
            .path("people.totalCount").entity(Int::class.java).isEqualTo(0)

        verify(exactly = 1) { catalog.listPeople(0, 20, "amy") }

        every { catalog.listPeople(0, 20, "") } returns ListPeopleResponse.newBuilder().setTotalCount(0).build()
        graphQl.document("{ people { totalCount } }").execute().path("people.totalCount").entity(Int::class.java).isEqualTo(0)
        verify(exactly = 1) { catalog.listPeople(0, 20, "") }
    }

    @Test
    fun `passes paging through unchanged, so the catalog can reject out-of-range values`() {
        every { catalog.listPeople(0, 500) } throws Status.INVALID_ARGUMENT.withDescription("page.limit: must be between 1 and 100").asRuntimeException()

        graphQl.document("{ people(page: {limit: 500}) { totalCount } }")
            .execute()
            .errors().satisfy { errors -> assertThat(errors.single().extensions["code"]).isEqualTo("BAD_REQUEST") }
    }

    @Test
    fun `a missing person is null, but a malformed id is an error`() {
        every { catalog.getPerson(PERSON) } throws Status.NOT_FOUND.asRuntimeException()
        every { catalog.getPerson("42") } throws Status.INVALID_ARGUMENT.withDescription("id: must be a UUID").asRuntimeException()

        graphQl.document("""{ person(id: "$PERSON") { id } }""").execute().path("person").valueIsNull()
        graphQl.document("""{ person(id: "42") { id } }""")
            .execute()
            .errors().satisfy { errors -> assertThat(errors.single().extensions["code"]).isEqualTo("BAD_REQUEST") }
    }

    @Test
    fun `createPerson sends the given fields and reports field errors in camelCase`() {
        val fields = slot<PersonFields>()
        every { catalog.createPerson(capture(fields)) } returns Person.newBuilder().setId(PERSON).setName("Amy Adams").setVersion(1).build()

        graphQl.document("""mutation { createPerson(input: {name: "Amy Adams", bio: "Actor.", birthDate: "1974-08-20"}) { id version } }""")
            .execute().path("createPerson.version").entity(Int::class.java).isEqualTo(1)
        assertThat(fields.captured.name).isEqualTo("Amy Adams")
        assertThat(fields.captured.bio).isEqualTo("Actor.")
        assertThat(fields.captured.birthDate).isEqualTo("1974-08-20")

        val badRequest = BadRequest.newBuilder()
            .addFieldViolations(BadRequest.FieldViolation.newBuilder().setField("birth_date").setDescription("cannot be in the future"))
            .build()
        every { catalog.createPerson(any()) } throws StatusProto.toStatusRuntimeException(
            com.google.rpc.Status.newBuilder().setCode(Code.INVALID_ARGUMENT_VALUE).setMessage("invalid").addDetails(Any.pack(badRequest)).build(),
        )
        graphQl.document("""mutation { createPerson(input: {name: "Amy Adams", birthDate: "3000-01-01"}) { id } }""")
            .execute()
            .errors().satisfy { errors ->
                assertThat(errors.single().extensions["fieldErrors"])
                    .isEqualTo(listOf(mapOf("field" to "birthDate", "message" to "cannot be in the future")))
            }
    }

    @Test
    fun `updatePerson sends the full field set, so omitted optional fields are cleared`() {
        val fields = slot<PersonFields>()
        every { catalog.updatePerson(PERSON, 3, capture(fields)) } returns Person.newBuilder().setId(PERSON).setName("Amy Adams").setVersion(4).build()

        graphQl.document("""mutation { updatePerson(id: "$PERSON", version: 3, input: {name: "Amy Adams"}) { version bio } }""")
            .execute()
            .path("updatePerson.version").entity(Int::class.java).isEqualTo(4)
            .path("updatePerson.bio").valueIsNull()

        assertThat(fields.captured.hasBio()).isFalse()
        assertThat(fields.captured.hasBirthDate()).isFalse()
    }

    @Test
    fun `deletePerson returns the id, and a stale version is a CONFLICT`() {
        every { catalog.deletePerson(PERSON, 2) } returns PERSON
        every { catalog.deletePerson(PERSON, 1) } throws Status.ABORTED.withDescription("stale").asRuntimeException()

        graphQl.document("""mutation { deletePerson(id: "$PERSON", version: 2) }""")
            .execute().path("deletePerson").entity(String::class.java).isEqualTo(PERSON)
        graphQl.document("""mutation { deletePerson(id: "$PERSON", version: 1) }""")
            .execute()
            .errors().satisfy { errors -> assertThat(errors.single().extensions["code"]).isEqualTo("CONFLICT") }

        verify(exactly = 1) { catalog.deletePerson(PERSON, 1) }
    }

    companion object {
        const val PERSON = "5b0f6d0e-6a55-4d4e-9d3e-1f0c2b7a9e11"
    }
}
