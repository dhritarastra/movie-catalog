package com.moviecatalog.gateway.search

import com.moviecatalog.catalog.v1.Credit
import com.moviecatalog.catalog.v1.CreditType
import com.moviecatalog.catalog.v1.Movie
import com.moviecatalog.catalog.v1.Person
import com.moviecatalog.catalog.v1.SearchHit
import com.moviecatalog.gateway.grpc.CatalogClient
import com.ninjasquad.springmockk.MockkBean
import io.grpc.Status
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester

/** The search query with the catalog mocked: union resolution, nested fields and error codes. */
@SpringBootTest
class SearchControllerTest(@Autowired graphQlService: ExecutionGraphQlService) {
    @MockkBean
    private lateinit var catalog: CatalogClient

    private val graphQl = ExecutionGraphQlServiceTester.create(graphQlService)

    @Test
    fun `hits come back in the catalog's order as Movie and Person, with their own fields`() {
        every { catalog.search("arr", 10) } returns listOf(
            SearchHit.newBuilder().setPerson(Person.newBuilder().setId(AMY).setName("Arrival Fan").setVersion(1)).build(),
            SearchHit.newBuilder().setMovie(Movie.newBuilder().setId(MOVIE).setTitle("Arrival").setReleaseDate("2016-11-11").setArtworkId(ARTWORK).setVersion(1)).build(),
        )
        every { catalog.creditsByPersonIds(listOf(AMY)) } returns mapOf(
            AMY to listOf(Credit.newBuilder().setId("c").setMovieId(MOVIE).setPersonId(AMY).setType(CreditType.CREDIT_TYPE_DIRECTOR).build()),
        )

        graphQl.document(
            """{ search(query: "arr") {
                 __typename
                 ... on Movie { title releaseDate artworkUrl(variant: THUMBNAIL) }
                 ... on Person { name credits { type } } } }""",
        )
            .execute()
            .path("search[*].__typename").entityList(String::class.java).containsExactly("Person", "Movie")
            .path("search[0].name").entity(String::class.java).isEqualTo("Arrival Fan")
            .path("search[0].credits[0].type").entity(String::class.java).isEqualTo("DIRECTOR")
            .path("search[1].title").entity(String::class.java).isEqualTo("Arrival")
            .path("search[1].artworkUrl").entity(String::class.java).isEqualTo("/media/$ARTWORK?variant=thumb")
    }

    @Test
    fun `an omitted limit means 10, and a given one is passed on unchanged`() {
        every { catalog.search(any(), any()) } returns emptyList()

        graphQl.document("""{ search(query: "x") { __typename } }""").execute().path("search").entityList(Any::class.java).hasSize(0)
        graphQl.document("""{ search(query: "x", limit: 99) { __typename } }""").execute()

        verify { catalog.search("x", 10) }
        verify { catalog.search("x", 99) }
    }

    @Test
    fun `an explicit null limit means the default of 10, as it does for paging`() {
        every { catalog.search("x", 10) } returns emptyList()

        graphQl.document("""{ search(query: "x", limit: null) { __typename } }""")
            .execute()
            .path("search").entityList(Any::class.java).hasSize(0)

        verify { catalog.search("x", 10) }
    }

    @Test
    fun `a catalog that is down or too slow is SERVICE_UNAVAILABLE`() {
        listOf(Status.UNAVAILABLE, Status.DEADLINE_EXCEEDED).forEach { status ->
            every { catalog.search("x", 10) } throws status.asRuntimeException()

            graphQl.document("""{ search(query: "x") { __typename } }""")
                .execute()
                .errors().satisfy { errors ->
                    assertThat(errors.single().extensions["code"]).describedAs(status.code.name).isEqualTo("SERVICE_UNAVAILABLE")
                }
        }
    }

    @Test
    fun `a rejected query is a BAD_REQUEST`() {
        every { catalog.search("  ", 10) } throws Status.INVALID_ARGUMENT.withDescription("query: is required").asRuntimeException()

        graphQl.document("""{ search(query: "  ") { __typename } }""")
            .execute()
            .errors().satisfy { errors -> assertThat(errors.single().extensions["code"]).isEqualTo("BAD_REQUEST") }
    }

    companion object {
        const val MOVIE = "70b3e304-c409-4e26-82c2-13889b1a9b4a"
        const val AMY = "8b4a4b1e-3f0c-4f5e-9d2a-1c2b3d4e5f60"
        const val ARTWORK = "11111111-1111-1111-1111-111111111111"
    }
}
