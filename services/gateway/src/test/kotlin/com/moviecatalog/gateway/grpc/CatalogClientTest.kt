package com.moviecatalog.gateway.grpc

import com.moviecatalog.catalog.v1.ArtworkChangeResponse
import com.moviecatalog.catalog.v1.AddCreditRequest
import com.moviecatalog.catalog.v1.Credit
import com.moviecatalog.catalog.v1.CreditServiceGrpc
import com.moviecatalog.catalog.v1.DeletePersonRequest
import com.moviecatalog.catalog.v1.DeletePersonResponse
import com.moviecatalog.catalog.v1.ListMoviesRequest
import com.moviecatalog.catalog.v1.ListMoviesResponse
import com.moviecatalog.catalog.v1.Movie
import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.catalog.v1.PersonServiceGrpc
import com.moviecatalog.catalog.v1.SetArtworkRequest
import com.moviecatalog.gateway.config.GatewayProperties
import com.moviecatalog.gateway.credit.CreditType
import io.grpc.Context
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.TimeUnit
import com.moviecatalog.catalog.v1.CreditType as CreditTypeProto

/** Request mapping and deadlines for catalog calls, against a fake catalog over in-process gRPC. */
class CatalogClientTest {
    private val name = InProcessServerBuilder.generateName()
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    @AfterEach
    fun tearDown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun `sends paging as given and carries the configured deadline`() {
        val movies = FakeMovies()
        val client = start(movies, Duration.ofSeconds(2))

        client.listMovies(40, 20)

        assertThat(movies.lastList!!.page.offset).isEqualTo(40)
        assertThat(movies.lastList!!.page.limit).isEqualTo(20)
        assertThat(movies.deadlineMillis).isBetween(1L, 2_000L)
    }

    @Test
    fun `sends the version the user saw with every change`() {
        val movies = FakeMovies()
        val client = start(movies, Duration.ofSeconds(2))

        client.setArtwork("movie", 7, "artwork")

        assertThat(movies.lastSetArtwork!!.expectedVersion).isEqualTo(7)
        assertThat(movies.lastSetArtwork!!.artworkId).isEqualTo("artwork")
    }

    @Test
    fun `a catalog that does not answer in time fails with DEADLINE_EXCEEDED instead of hanging`() {
        val client = start(FakeMovies(hang = true), Duration.ofMillis(100))

        val error = assertThrows<StatusRuntimeException> { client.listMovies(0, 20) }

        assertThat(error.status.code).isEqualTo(Status.Code.DEADLINE_EXCEEDED)
    }

    @Test
    fun `deletePerson returns the id the catalog confirmed`() {
        val client = start(FakeMovies(), Duration.ofSeconds(2))

        assertThat(client.deletePerson("person", 3)).isEqualTo("person-deleted")
    }

    @Test
    fun `addCredit sends the role and billing order, and leaves out a missing character`() {
        val client = start(FakeMovies(), Duration.ofSeconds(2))

        client.addCredit("movie", "person", CreditType.DIRECTOR, null, 2)
        assertThat(credits.lastAdd!!.type).isEqualTo(CreditTypeProto.CREDIT_TYPE_DIRECTOR)
        assertThat(credits.lastAdd!!.hasCharacterName()).isFalse()
        assertThat(credits.lastAdd!!.billingOrder).isEqualTo(2)

        client.addCredit("movie", "person", CreditType.CAST, "Louise Banks", 0)
        assertThat(credits.lastAdd!!.characterName).isEqualTo("Louise Banks")
    }

    private val credits = FakeCredits()

    private fun start(movies: FakeMovies, deadline: Duration): CatalogClient {
        server = InProcessServerBuilder.forName(name).addService(movies).addService(FakePeople()).addService(credits).build().start()
        channel = InProcessChannelBuilder.forName(name).build()
        return CatalogClient(
            MovieServiceGrpc.newBlockingStub(channel),
            PersonServiceGrpc.newBlockingStub(channel),
            CreditServiceGrpc.newBlockingStub(channel),
            GatewayProperties(5_242_880, deadline, Duration.ofSeconds(10), Duration.ofSeconds(10), 64 * 1024, Duration.ofSeconds(60)),
        )
    }

    private class FakeMovies(private val hang: Boolean = false) : MovieServiceGrpc.MovieServiceImplBase() {
        var lastList: ListMoviesRequest? = null
        var lastSetArtwork: SetArtworkRequest? = null
        var deadlineMillis: Long? = null

        override fun listMovies(request: ListMoviesRequest, responseObserver: StreamObserver<ListMoviesResponse>) {
            lastList = request
            deadlineMillis = Context.current().deadline?.timeRemaining(TimeUnit.MILLISECONDS)
            if (hang) return
            responseObserver.onNext(ListMoviesResponse.getDefaultInstance())
            responseObserver.onCompleted()
        }

        override fun setArtwork(request: SetArtworkRequest, responseObserver: StreamObserver<ArtworkChangeResponse>) {
            lastSetArtwork = request
            responseObserver.onNext(ArtworkChangeResponse.newBuilder().setMovie(Movie.newBuilder().setId(request.movieId)).build())
            responseObserver.onCompleted()
        }
    }

    private class FakePeople : PersonServiceGrpc.PersonServiceImplBase() {
        override fun deletePerson(request: DeletePersonRequest, responseObserver: StreamObserver<DeletePersonResponse>) {
            responseObserver.onNext(DeletePersonResponse.newBuilder().setId("${request.id}-deleted").build())
            responseObserver.onCompleted()
        }
    }

    private class FakeCredits : CreditServiceGrpc.CreditServiceImplBase() {
        var lastAdd: AddCreditRequest? = null

        override fun addCredit(request: AddCreditRequest, responseObserver: StreamObserver<Credit>) {
            lastAdd = request
            responseObserver.onNext(Credit.newBuilder().setId("credit").build())
            responseObserver.onCompleted()
        }
    }
}
