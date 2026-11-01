package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.CATALOG_IN_PROCESS_NAME
import com.moviecatalog.catalog.CatalogGrpcTest
import com.moviecatalog.catalog.badRequestFields
import com.moviecatalog.catalog.v1.CreatePersonRequest
import com.moviecatalog.catalog.v1.DeletePersonRequest
import com.moviecatalog.catalog.v1.GetPeopleByIdsRequest
import com.moviecatalog.catalog.v1.GetPersonRequest
import com.moviecatalog.catalog.v1.ListPeopleRequest
import com.moviecatalog.catalog.v1.PageRequest
import com.moviecatalog.catalog.v1.PersonFields
import com.moviecatalog.catalog.v1.PersonServiceGrpc
import com.moviecatalog.catalog.v1.UpdatePersonRequest
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import com.moviecatalog.catalog.v1.Person as PersonProto

@CatalogGrpcTest
class PersonGrpcServiceTest(@Autowired private val jdbc: JdbcClient) {
    private val channel: ManagedChannel = InProcessChannelBuilder.forName(CATALOG_IN_PROCESS_NAME).directExecutor().build()
    private val people = PersonServiceGrpc.newBlockingStub(channel)

    @BeforeEach
    fun emptyTable() {
        // Credits first: a credited person cannot be deleted.
        jdbc.sql("DELETE FROM credit").update()
        jdbc.sql("DELETE FROM person").update()
    }

    @AfterEach
    fun closeChannel() {
        channel.shutdownNow()
    }

    @Test
    fun `creates a person with normalised fields at version 1`() {
        val created = create(PersonFields.newBuilder().setName("  Amy Adams ").setBio("   ").setBirthDate("1974-08-20"))

        assertThat(created.name).isEqualTo("Amy Adams")
        assertThat(created.hasBio()).isFalse()
        assertThat(created.birthDate).isEqualTo("1974-08-20")
        assertThat(created.version).isEqualTo(1)
        assertThat(get(created.id)).isEqualTo(created)
    }

    @Test
    fun `rejects invalid fields with a BadRequest detail per field`() {
        val error = assertThrows<StatusRuntimeException> {
            create(PersonFields.newBuilder().setName(" ").setBirthDate("1974-02-30"))
        }

        assertThat(error.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
        assertThat(badRequestFields(error)).containsExactly("name", "birth_date")
    }

    @Test
    fun `reports unknown and malformed ids differently`() {
        val unknown = assertThrows<StatusRuntimeException> { get(UUID.randomUUID().toString()) }
        assertThat(unknown.status.code).isEqualTo(Status.Code.NOT_FOUND)

        val malformed = assertThrows<StatusRuntimeException> { get("not-a-uuid") }
        assertThat(malformed.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
        assertThat(badRequestFields(malformed)).containsExactly("id")
    }

    @Test
    fun `lists alphabetically with ICU, keeps people who share a name, and pages stably`() {
        listOf("denis Villeneuve", "Amy Adams", "Jeremy Renner", "Émile Hirsch", "Amy Adams")
            .forEach { create(PersonFields.newBuilder().setName(it)) }

        val all = list(offset = 0, limit = 10)
        val page1 = list(offset = 0, limit = 2)
        val page2 = list(offset = 2, limit = 3)

        // Byte order would put "Émile" after "Jeremy".
        assertThat(all.peopleList.map { it.name })
            .containsExactly("Amy Adams", "Amy Adams", "denis Villeneuve", "Émile Hirsch", "Jeremy Renner")
        assertThat((page1.peopleList + page2.peopleList).map { it.id }).isEqualTo(all.peopleList.map { it.id })
        assertThat(listOf(all, page1, page2).map { it.totalCount }).containsOnly(5)
    }

    @Test
    fun `filters by part of the name, ignoring case, and counts only the matches`() {
        listOf("Amy Adams", "Jeremy Renner", "amy poehler").forEach { create(PersonFields.newBuilder().setName(it)) }

        val found = people.listPeople(
            ListPeopleRequest.newBuilder().setPage(PageRequest.newBuilder().setOffset(0).setLimit(1)).setNameContains("AMY").build(),
        )

        assertThat(found.peopleList.map { it.name }).containsExactly("Amy Adams")
        assertThat(found.totalCount).isEqualTo(2)
        val whitespace = people.listPeople(
            ListPeopleRequest.newBuilder().setPage(PageRequest.newBuilder().setLimit(10)).setNameContains(" \t ").build(),
        )
        assertThat(whitespace.totalCount).describedAs("a whitespace-only filter lists everyone").isEqualTo(3)
        val tooLong = assertThrows<StatusRuntimeException> {
            people.listPeople(
                ListPeopleRequest.newBuilder().setPage(PageRequest.newBuilder().setLimit(10)).setNameContains("x".repeat(201)).build(),
            )
        }
        assertThat(badRequestFields(tooLong)).containsExactly("name_contains")
    }

    @Test
    fun `rejects out-of-range paging, including an omitted limit`() {
        listOf(0 to 0, 0 to 101, -1 to 10).forEach { (offset, limit) ->
            val error = assertThrows<StatusRuntimeException> { list(offset, limit) }
            assertThat(error.status.code).describedAs("offset=$offset limit=$limit").isEqualTo(Status.Code.INVALID_ARGUMENT)
        }
        val omitted = assertThrows<StatusRuntimeException> { people.listPeople(ListPeopleRequest.getDefaultInstance()) }
        assertThat(omitted.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
    }

    @Test
    fun `an update replaces every editable field and bumps the version`() {
        val person = create(PersonFields.newBuilder().setName("Amy Adams").setBio("Actor.").setBirthDate("1974-08-20"))

        // Only the name is sent, so bio and birth date are cleared.
        val updated = people.updatePerson(update(person.id, version = 1, PersonFields.newBuilder().setName(" Amy Lou Adams ")))

        assertThat(updated.name).isEqualTo("Amy Lou Adams")
        assertThat(updated.hasBio()).isFalse()
        assertThat(updated.hasBirthDate()).isFalse()
        assertThat(updated.version).isEqualTo(2)
        assertThat(get(person.id)).isEqualTo(updated)
    }

    @Test
    fun `an update from a stale version is a conflict and changes nothing`() {
        val person = create(PersonFields.newBuilder().setName("Amy Adams"))
        people.updatePerson(update(person.id, version = 1, PersonFields.newBuilder().setName("First edit")))

        val error = assertThrows<StatusRuntimeException> {
            people.updatePerson(update(person.id, version = 1, PersonFields.newBuilder().setName("Second edit")))
        }

        assertThat(error.status.code).isEqualTo(Status.Code.ABORTED)
        assertThat(error.status.description).contains("version 2, not 1")
        assertThat(get(person.id).name).isEqualTo("First edit")
    }

    @Test
    fun `an update reports invalid fields, an omitted version and an unknown person`() {
        val person = create(PersonFields.newBuilder().setName("Amy Adams"))

        val invalid = assertThrows<StatusRuntimeException> {
            people.updatePerson(update(person.id, version = 1, PersonFields.newBuilder().setName("").setBirthDate("3000-01-01")))
        }
        assertThat(badRequestFields(invalid)).containsExactly("name", "birth_date")

        val omitted = assertThrows<StatusRuntimeException> {
            people.updatePerson(update(person.id, version = 0, PersonFields.newBuilder().setName("Edit")))
        }
        assertThat(badRequestFields(omitted)).containsExactly("expected_version")

        val unknown = assertThrows<StatusRuntimeException> {
            people.updatePerson(update(UUID.randomUUID().toString(), version = 1, PersonFields.newBuilder().setName("Edit")))
        }
        assertThat(unknown.status.code).isEqualTo(Status.Code.NOT_FOUND)
        assertThat(get(person.id).version).isEqualTo(1)
    }

    @Test
    fun `deleting removes the person and returns their id`() {
        val person = create(PersonFields.newBuilder().setName("Amy Adams"))

        assertThat(people.deletePerson(delete(person.id, version = 1)).id).isEqualTo(person.id)

        val gone = assertThrows<StatusRuntimeException> { get(person.id) }
        assertThat(gone.status.code).isEqualTo(Status.Code.NOT_FOUND)
    }

    @Test
    fun `deleting from a stale or omitted version keeps the person, and deleting twice reports them gone`() {
        val person = create(PersonFields.newBuilder().setName("Amy Adams"))
        people.updatePerson(update(person.id, version = 1, PersonFields.newBuilder().setName("Edited")))

        val stale = assertThrows<StatusRuntimeException> { people.deletePerson(delete(person.id, version = 1)) }
        assertThat(stale.status.code).isEqualTo(Status.Code.ABORTED)
        val omitted = assertThrows<StatusRuntimeException> { people.deletePerson(delete(person.id, version = 0)) }
        assertThat(badRequestFields(omitted)).containsExactly("expected_version")
        assertThat(get(person.id).name).isEqualTo("Edited")

        people.deletePerson(delete(person.id, version = 2))
        val again = assertThrows<StatusRuntimeException> { people.deletePerson(delete(person.id, version = 2)) }
        assertThat(again.status.code).isEqualTo(Status.Code.NOT_FOUND)
    }

    @Test
    fun `saving identical fields still advances the version and rejects the old version`() {
        val fields = PersonFields.newBuilder().setName("Amy Adams")
        val person = create(fields)
        val saved = people.updatePerson(update(person.id, person.version, fields))

        assertThat(saved.version).isEqualTo(2)
        assertThat(get(person.id).version).isEqualTo(2)
        val stale = assertThrows<StatusRuntimeException> { people.updatePerson(update(person.id, person.version, fields)) }
        assertThat(stale.status.code).isEqualTo(Status.Code.ABORTED)
    }

    @Test
    fun `looks up several people at once, leaving out ids that do not exist`() {
        val amy = create(PersonFields.newBuilder().setName("Amy Adams"))
        val missing = UUID.randomUUID().toString()

        val found = people.getPeopleByIds(GetPeopleByIdsRequest.newBuilder().addAllIds(listOf(amy.id, missing)).build())

        assertThat(found.peopleMap.keys).containsExactly(amy.id)
        assertThat(found.peopleMap[amy.id]!!.name).isEqualTo("Amy Adams")
    }

    private fun create(fields: PersonFields.Builder): PersonProto =
        people.createPerson(CreatePersonRequest.newBuilder().setFields(fields).build())

    private fun get(id: String): PersonProto = people.getPerson(GetPersonRequest.newBuilder().setId(id).build())

    private fun list(offset: Int, limit: Int) =
        people.listPeople(ListPeopleRequest.newBuilder().setPage(PageRequest.newBuilder().setOffset(offset).setLimit(limit)).build())

    private fun update(id: String, version: Int, fields: PersonFields.Builder) =
        UpdatePersonRequest.newBuilder().setId(id).setExpectedVersion(version).setFields(fields).build()

    private fun delete(id: String, version: Int) = DeletePersonRequest.newBuilder().setId(id).setExpectedVersion(version).build()
}
