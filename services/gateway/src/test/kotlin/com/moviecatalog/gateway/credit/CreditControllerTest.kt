package com.moviecatalog.gateway.credit

import com.google.protobuf.Any
import com.google.rpc.BadRequest
import com.google.rpc.Code
import com.moviecatalog.catalog.v1.Credit
import com.moviecatalog.catalog.v1.ListMoviesResponse
import com.moviecatalog.catalog.v1.Movie
import com.moviecatalog.catalog.v1.Person
import com.moviecatalog.gateway.grpc.CatalogClient
import com.ninjasquad.springmockk.MockkBean
import io.grpc.Status
import io.grpc.protobuf.StatusProto
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester
import com.moviecatalog.catalog.v1.CreditType as CreditTypeProto

/** The credit mutations and nested credit fields with the catalog mocked: mapping, batching and error codes. */
@SpringBootTest
class CreditControllerTest(@Autowired graphQlService: ExecutionGraphQlService) {
    @MockkBean
    private lateinit var catalog: CatalogClient

    private val graphQl = ExecutionGraphQlServiceTester.create(graphQlService)

    @Test
    fun `addCredit passes every field on and returns the credit with its person`() {
        every { catalog.addCredit(MOVIE, AMY, CreditType.CAST, "Louise Banks", 1) } returns credit("c1", AMY, CreditTypeProto.CREDIT_TYPE_CAST, "Louise Banks", 1)
        every { catalog.peopleByIds(listOf(AMY)) } returns mapOf(AMY to person(AMY, "Amy Adams"))

        graphQl.document(
            """mutation { addCredit(input: {movieId: "$MOVIE", personId: "$AMY", type: CAST, characterName: "Louise Banks", billingOrder: 1}) {
                 id type characterName billingOrder person { name } } }""",
        )
            .execute()
            .path("addCredit.type").entity(String::class.java).isEqualTo("CAST")
            .path("addCredit.characterName").entity(String::class.java).isEqualTo("Louise Banks")
            .path("addCredit.person.name").entity(String::class.java).isEqualTo("Amy Adams")
    }

    @Test
    fun `an omitted character name is sent as none`() {
        every { catalog.addCredit(MOVIE, DENIS, CreditType.DIRECTOR, null, 0) } returns credit("c2", DENIS, CreditTypeProto.CREDIT_TYPE_DIRECTOR, null, 0)

        graphQl.document("""mutation { addCredit(input: {movieId: "$MOVIE", personId: "$DENIS", type: DIRECTOR, billingOrder: 0}) { characterName } }""")
            .execute()
            .path("addCredit.characterName").valueIsNull()
    }

    @Test
    fun `field errors come back in camelCase, and a duplicate is a CONFLICT`() {
        val badRequest = BadRequest.newBuilder()
            .addFieldViolations(BadRequest.FieldViolation.newBuilder().setField("character_name").setDescription("only cast credits have a character"))
            .build()
        every { catalog.addCredit(MOVIE, DENIS, CreditType.WRITER, "Louise", 0) } throws StatusProto.toStatusRuntimeException(
            com.google.rpc.Status.newBuilder().setCode(Code.INVALID_ARGUMENT_VALUE).setMessage("invalid").addDetails(Any.pack(badRequest)).build(),
        )
        every { catalog.addCredit(MOVIE, DENIS, CreditType.DIRECTOR, null, 0) } throws
            Status.ALREADY_EXISTS.withDescription("This person already has this role on this movie").asRuntimeException()

        graphQl.document("""mutation { addCredit(input: {movieId: "$MOVIE", personId: "$DENIS", type: WRITER, characterName: "Louise", billingOrder: 0}) { id } }""")
            .execute()
            .errors().satisfy { errors ->
                assertThat(errors.single().extensions["code"]).isEqualTo("BAD_REQUEST")
                assertThat(errors.single().extensions["fieldErrors"])
                    .isEqualTo(listOf(mapOf("field" to "characterName", "message" to "only cast credits have a character")))
            }
        graphQl.document("""mutation { addCredit(input: {movieId: "$MOVIE", personId: "$DENIS", type: DIRECTOR, billingOrder: 0}) { id } }""")
            .execute()
            .errors().satisfy { errors ->
                assertThat(errors.single().extensions["code"]).isEqualTo("CONFLICT")
                assertThat(errors.single().message).isEqualTo("This person already has this role on this movie")
            }
    }

    @Test
    fun `updateCredit replaces the role, character and billing order`() {
        every { catalog.updateCredit("c1", CreditType.PRODUCER, null, 4) } returns credit("c1", AMY, CreditTypeProto.CREDIT_TYPE_PRODUCER, null, 4)

        graphQl.document("""mutation { updateCredit(id: "c1", input: {type: PRODUCER, billingOrder: 4}) { type characterName billingOrder } }""")
            .execute()
            .path("updateCredit.type").entity(String::class.java).isEqualTo("PRODUCER")
            .path("updateCredit.characterName").valueIsNull()
            .path("updateCredit.billingOrder").entity(Int::class.java).isEqualTo(4)
    }

    @Test
    fun `removeCredit returns the id, and an unknown credit is NOT_FOUND`() {
        every { catalog.removeCredit("c1") } returns "c1"
        every { catalog.removeCredit("gone") } throws Status.NOT_FOUND.withDescription("Credit gone not found").asRuntimeException()

        graphQl.document("""mutation { removeCredit(id: "c1") }""").execute().path("removeCredit").entity(String::class.java).isEqualTo("c1")
        graphQl.document("""mutation { removeCredit(id: "gone") }""")
            .execute()
            .errors().satisfy { errors -> assertThat(errors.single().extensions["code"]).isEqualTo("NOT_FOUND") }
    }

    @Test
    fun `a page of movies loads cast and creators with one call per field, split by role`() {
        val dune = "22222222-2222-2222-2222-222222222222"
        every { catalog.listMovies(0, 20) } returns ListMoviesResponse.newBuilder()
            .addMovies(movie(MOVIE, "Arrival"))
            .addMovies(movie(dune, "Dune"))
            .setTotalCount(2)
            .build()
        every { catalog.creditsByMovieIds(listOf(MOVIE, dune)) } returns mapOf(
            MOVIE to listOf(
                credit("c2", DENIS, CreditTypeProto.CREDIT_TYPE_DIRECTOR, null, 0),
                credit("c1", AMY, CreditTypeProto.CREDIT_TYPE_CAST, "Louise Banks", 1),
            ),
            dune to emptyList(),
        )

        graphQl.document("{ movies { items { title cast { id characterName } creators { id type } } } }")
            .execute()
            .path("movies.items[0].cast[*].id").entityList(String::class.java).containsExactly("c1")
            .path("movies.items[0].creators[*].type").entityList(String::class.java).containsExactly("DIRECTOR")
            .path("movies.items[1].cast").entityList(Map::class.java).hasSize(0)

        verify(exactly = 2) { catalog.creditsByMovieIds(any()) }
    }

    @Test
    fun `a person's credits come with their movies, looked up once for all credits`() {
        every { catalog.getPerson(AMY) } returns person(AMY, "Amy Adams")
        every { catalog.creditsByPersonIds(listOf(AMY)) } returns mapOf(
            AMY to listOf(
                credit("c1", AMY, CreditTypeProto.CREDIT_TYPE_CAST, "Louise Banks", 1),
                credit("c3", AMY, CreditTypeProto.CREDIT_TYPE_PRODUCER, null, 5),
            ),
        )
        every { catalog.moviesByIds(listOf(MOVIE)) } returns mapOf(MOVIE to movie(MOVIE, "Arrival"))

        graphQl.document("""{ person(id: "$AMY") { credits { type movie { title } } } }""")
            .execute()
            .path("person.credits[*].type").entityList(String::class.java).containsExactly("CAST", "PRODUCER")
            .path("person.credits[*].movie.title").entityList(String::class.java).containsExactly("Arrival", "Arrival")

        verify(exactly = 1) { catalog.moviesByIds(listOf(MOVIE)) }
    }

    @Test
    fun `a credit whose movie was deleted mid-request is null, and the rest of the person still loads`() {
        every { catalog.getPerson(AMY) } returns person(AMY, "Amy Adams")
        every { catalog.creditsByPersonIds(listOf(AMY)) } returns mapOf(AMY to listOf(credit("c1", AMY, CreditTypeProto.CREDIT_TYPE_CAST, null, 0)))
        every { catalog.moviesByIds(listOf(MOVIE)) } returns emptyMap()

        graphQl.document("""{ person(id: "$AMY") { name credits { id movie { title } } } }""")
            .execute()
            .path("person.name").entity(String::class.java).isEqualTo("Amy Adams")
            .path("person.credits[0].movie").valueIsNull()
    }

    private fun credit(id: String, personId: String, type: CreditTypeProto, character: String?, billing: Int): Credit =
        Credit.newBuilder()
            .setId(id)
            .setMovieId(MOVIE)
            .setPersonId(personId)
            .setType(type)
            .setBillingOrder(billing)
            .apply { character?.let { setCharacterName(it) } }
            .build()

    private fun movie(id: String, title: String): Movie = Movie.newBuilder().setId(id).setTitle(title).setVersion(1).build()

    private fun person(id: String, name: String): Person = Person.newBuilder().setId(id).setName(name).setVersion(1).build()

    companion object {
        const val MOVIE = "70b3e304-c409-4e26-82c2-13889b1a9b4a"
        const val AMY = "8b4a4b1e-3f0c-4f5e-9d2a-1c2b3d4e5f60"
        const val DENIS = "9c5b5c2f-4a1d-4a6f-8e3b-2d3c4e5f6a71"
    }
}
