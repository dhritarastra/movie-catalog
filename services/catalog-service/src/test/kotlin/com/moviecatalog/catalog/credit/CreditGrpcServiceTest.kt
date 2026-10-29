package com.moviecatalog.catalog.credit

import com.moviecatalog.catalog.CATALOG_IN_PROCESS_NAME
import com.moviecatalog.catalog.CatalogGrpcTest
import com.moviecatalog.catalog.badRequestFields
import com.moviecatalog.catalog.v1.AddCreditRequest
import com.moviecatalog.catalog.v1.CreateMovieRequest
import com.moviecatalog.catalog.v1.CreatePersonRequest
import com.moviecatalog.catalog.v1.CreditServiceGrpc
import com.moviecatalog.catalog.v1.CreditType
import com.moviecatalog.catalog.v1.DeleteMovieRequest
import com.moviecatalog.catalog.v1.DeletePersonRequest
import com.moviecatalog.catalog.v1.GetCreditsByMovieIdsRequest
import com.moviecatalog.catalog.v1.GetCreditsByPersonIdsRequest
import com.moviecatalog.catalog.v1.GetMovieRequest
import com.moviecatalog.catalog.v1.GetPersonRequest
import com.moviecatalog.catalog.v1.MovieFields
import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.catalog.v1.PersonFields
import com.moviecatalog.catalog.v1.PersonServiceGrpc
import com.moviecatalog.catalog.v1.RemoveCreditRequest
import com.moviecatalog.catalog.v1.UpdateCreditRequest
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
import com.moviecatalog.catalog.v1.Credit as CreditProto

/** Drives the real credit endpoint in-process against Postgres, with real movies and people. */
@CatalogGrpcTest
class CreditGrpcServiceTest(@Autowired private val jdbc: JdbcClient) {
    private val channel: ManagedChannel = InProcessChannelBuilder.forName(CATALOG_IN_PROCESS_NAME).directExecutor().build()
    private val credits = CreditServiceGrpc.newBlockingStub(channel)
    private val movies = MovieServiceGrpc.newBlockingStub(channel)
    private val people = PersonServiceGrpc.newBlockingStub(channel)

    private lateinit var movie: String
    private lateinit var amy: String
    private lateinit var denis: String

    @BeforeEach
    fun setUp() {
        jdbc.sql("DELETE FROM credit").update()
        jdbc.sql("DELETE FROM movie").update()
        jdbc.sql("DELETE FROM person").update()
        movie = movies.createMovie(CreateMovieRequest.newBuilder().setFields(MovieFields.newBuilder().setTitle("Arrival")).build()).id
        amy = person("Amy Adams")
        denis = person("Denis Villeneuve")
    }

    @AfterEach
    fun closeChannel() {
        channel.shutdownNow()
    }

    @Test
    fun `adds cast and creator credits with a normalised character name`() {
        val cast = add(amy, CreditType.CREDIT_TYPE_CAST, character = "  Louise Banks ", billing = 1)
        val director = add(denis, CreditType.CREDIT_TYPE_DIRECTOR, character = "   ", billing = 0)

        assertThat(cast.movieId).isEqualTo(movie)
        assertThat(cast.personId).isEqualTo(amy)
        assertThat(cast.characterName).isEqualTo("Louise Banks")
        assertThat(director.hasCharacterName()).isFalse()
    }

    @Test
    fun `rejects an omitted role, a character on a creator credit and a negative billing order, all at once`() {
        val error = assertThrows<StatusRuntimeException> {
            credits.addCredit(
                AddCreditRequest.newBuilder().setMovieId(movie).setPersonId(amy).setCharacterName("Louise").setBillingOrder(-1).build(),
            )
        }
        assertThat(badRequestFields(error)).containsExactly("type", "billing_order")

        val creator = assertThrows<StatusRuntimeException> { add(denis, CreditType.CREDIT_TYPE_WRITER, character = "Louise") }
        assertThat(badRequestFields(creator)).containsExactly("character_name")
    }

    @Test
    fun `an unknown movie or person is NOT_FOUND, and malformed ids are INVALID_ARGUMENT`() {
        val unknownMovie = assertThrows<StatusRuntimeException> { add(amy, CreditType.CREDIT_TYPE_CAST, movieId = UUID.randomUUID().toString()) }
        val unknownPerson = assertThrows<StatusRuntimeException> { add(UUID.randomUUID().toString(), CreditType.CREDIT_TYPE_CAST) }
        val malformed = assertThrows<StatusRuntimeException> { add("42", CreditType.CREDIT_TYPE_CAST, movieId = "nope") }

        assertThat(unknownMovie.status.code).isEqualTo(Status.Code.NOT_FOUND)
        assertThat(unknownPerson.status.code).isEqualTo(Status.Code.NOT_FOUND)
        assertThat(badRequestFields(malformed)).containsExactly("movie_id")
    }

    @Test
    fun `the same role twice is ALREADY_EXISTS, but a second role or character is allowed`() {
        add(denis, CreditType.CREDIT_TYPE_DIRECTOR)
        add(denis, CreditType.CREDIT_TYPE_WRITER)
        add(amy, CreditType.CREDIT_TYPE_CAST, character = "Louise Banks")
        add(amy, CreditType.CREDIT_TYPE_CAST, character = "Narrator")

        val twice = assertThrows<StatusRuntimeException> { add(denis, CreditType.CREDIT_TYPE_DIRECTOR, billing = 9) }

        assertThat(twice.status.code).isEqualTo(Status.Code.ALREADY_EXISTS)
        assertThat(byMovie(movie).map { it.type }).hasSize(4)
    }

    @Test
    fun `lists credits by movie and by person in billing order, with an empty entry for ids with none`() {
        add(amy, CreditType.CREDIT_TYPE_CAST, character = "Louise Banks", billing = 2)
        add(denis, CreditType.CREDIT_TYPE_DIRECTOR, billing = 0)
        val noCredits = movies.createMovie(CreateMovieRequest.newBuilder().setFields(MovieFields.newBuilder().setTitle("Dune")).build()).id

        val response = credits.getCreditsByMovieIds(GetCreditsByMovieIdsRequest.newBuilder().addMovieIds(movie).addMovieIds(noCredits).build())

        assertThat(response.creditsMap[movie]!!.creditsList.map { it.personId }).containsExactly(denis, amy)
        assertThat(response.creditsMap[noCredits]!!.creditsList).isEmpty()
        val byPerson = credits.getCreditsByPersonIds(GetCreditsByPersonIdsRequest.newBuilder().addPersonIds(amy).build())
        assertThat(byPerson.creditsMap[amy]!!.creditsList.map { it.characterName }).containsExactly("Louise Banks")
    }

    @Test
    fun `an update replaces the role, character and billing order, and keeps the movie and person`() {
        val credit = add(amy, CreditType.CREDIT_TYPE_CAST, character = "Louise Banks", billing = 1)

        val updated = credits.updateCredit(
            UpdateCreditRequest.newBuilder().setId(credit.id).setType(CreditType.CREDIT_TYPE_PRODUCER).setBillingOrder(3).build(),
        )

        assertThat(updated.type).isEqualTo(CreditType.CREDIT_TYPE_PRODUCER)
        assertThat(updated.hasCharacterName()).isFalse()
        assertThat(updated.billingOrder).isEqualTo(3)
        assertThat(updated.movieId).isEqualTo(movie)
        assertThat(updated.personId).isEqualTo(amy)
    }

    @Test
    fun `an update that would duplicate another credit is ALREADY_EXISTS and changes nothing`() {
        add(denis, CreditType.CREDIT_TYPE_DIRECTOR)
        val writer = add(denis, CreditType.CREDIT_TYPE_WRITER)

        val error = assertThrows<StatusRuntimeException> {
            credits.updateCredit(UpdateCreditRequest.newBuilder().setId(writer.id).setType(CreditType.CREDIT_TYPE_DIRECTOR).build())
        }

        assertThat(error.status.code).isEqualTo(Status.Code.ALREADY_EXISTS)
        assertThat(byMovie(movie).map { it.type }).containsExactlyInAnyOrder(CreditType.CREDIT_TYPE_DIRECTOR, CreditType.CREDIT_TYPE_WRITER)
    }

    @Test
    fun `updating an unknown credit is NOT_FOUND`() {
        val error = assertThrows<StatusRuntimeException> {
            credits.updateCredit(UpdateCreditRequest.newBuilder().setId(UUID.randomUUID().toString()).setType(CreditType.CREDIT_TYPE_CAST).build())
        }

        assertThat(error.status.code).isEqualTo(Status.Code.NOT_FOUND)
    }

    @Test
    fun `removing a credit keeps the person, and removing it twice is NOT_FOUND`() {
        val credit = add(amy, CreditType.CREDIT_TYPE_CAST)

        assertThat(credits.removeCredit(RemoveCreditRequest.newBuilder().setId(credit.id).build()).id).isEqualTo(credit.id)
        val again = assertThrows<StatusRuntimeException> { credits.removeCredit(RemoveCreditRequest.newBuilder().setId(credit.id).build()) }

        assertThat(again.status.code).isEqualTo(Status.Code.NOT_FOUND)
        assertThat(byMovie(movie)).isEmpty()
        assertThat(jdbc.sql("SELECT count(*) FROM person WHERE id = ?::uuid").param(amy).query(Int::class.java).single()).isEqualTo(1)
    }

    @Test
    fun `a credited person cannot be deleted until their credits are removed`() {
        val credit = add(amy, CreditType.CREDIT_TYPE_CAST)

        val refused = assertThrows<StatusRuntimeException> { people.deletePerson(DeletePersonRequest.newBuilder().setId(amy).setExpectedVersion(1).build()) }
        assertThat(refused.status.code).isEqualTo(Status.Code.FAILED_PRECONDITION)
        assertThat(refused.status.description).contains("Remove their credits first")

        credits.removeCredit(RemoveCreditRequest.newBuilder().setId(credit.id).build())
        people.deletePerson(DeletePersonRequest.newBuilder().setId(amy).setExpectedVersion(1).build())
    }

    @Test
    fun `deleting a movie removes its credits and keeps the people`() {
        add(amy, CreditType.CREDIT_TYPE_CAST)

        movies.deleteMovie(DeleteMovieRequest.newBuilder().setId(movie).setExpectedVersion(1).build())

        val left = credits.getCreditsByPersonIds(GetCreditsByPersonIdsRequest.newBuilder().addPersonIds(amy).build())
        assertThat(left.creditsMap[amy]!!.creditsList).isEmpty()
        people.deletePerson(DeletePersonRequest.newBuilder().setId(amy).setExpectedVersion(1).build())
    }

    @Test
    fun `adding, editing and removing credits does not change the movie's or the person's version`() {
        val credit = add(amy, CreditType.CREDIT_TYPE_CAST)
        credits.updateCredit(UpdateCreditRequest.newBuilder().setId(credit.id).setType(CreditType.CREDIT_TYPE_PRODUCER).build())
        credits.removeCredit(RemoveCreditRequest.newBuilder().setId(credit.id).build())

        assertThat(movies.getMovie(GetMovieRequest.newBuilder().setId(movie).build()).version).isEqualTo(1)
        assertThat(people.getPerson(GetPersonRequest.newBuilder().setId(amy).build()).version).isEqualTo(1)
    }

    private fun person(name: String): String =
        people.createPerson(CreatePersonRequest.newBuilder().setFields(PersonFields.newBuilder().setName(name)).build()).id

    private fun add(personId: String, type: CreditType, character: String? = null, billing: Int = 0, movieId: String = movie): CreditProto =
        credits.addCredit(
            AddCreditRequest.newBuilder()
                .setMovieId(movieId)
                .setPersonId(personId)
                .setType(type)
                .setBillingOrder(billing)
                .apply { character?.let { setCharacterName(it) } }
                .build(),
        )

    private fun byMovie(id: String): List<CreditProto> =
        credits.getCreditsByMovieIds(GetCreditsByMovieIdsRequest.newBuilder().addMovieIds(id).build()).creditsMap[id]!!.creditsList
}
