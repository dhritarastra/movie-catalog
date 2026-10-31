package com.moviecatalog.gateway.tmdb

import com.moviecatalog.gateway.graphql.ErrorCode
import com.moviecatalog.gateway.graphql.GatewayException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class TmdbClientTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = TmdbClient(TmdbProperties(apiToken = "secret", apiUrl = API, imageUrl = IMAGES), builder.build())

    @Test
    fun `searches by title and year with the token, and takes the first result with a poster`() {
        server.expect(requestTo("$API/search/movie?query=Arrival&include_adult=false&year=2016"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
            .andRespond(json("""{"results": [{"id": 1, "title": "Arrival", "poster_path": null}, $ARRIVAL]}"""))

        val poster = client.findPoster("Arrival", "2016")

        assertThat(poster).isEqualTo(TmdbPoster(329865, "Arrival", "2016", "/arrival.jpg"))
        assertThat(client.previewUrl(poster!!)).isEqualTo("$IMAGES/w342/arrival.jpg")
        server.verify()
    }

    @Test
    fun `searches without the year when the year finds nothing`() {
        server.expect(requestTo("$API/search/movie?query=Spirited%20Away&include_adult=false&year=2002"))
            .andRespond(json("""{"results": []}"""))
        server.expect(requestTo("$API/search/movie?query=Spirited%20Away&include_adult=false"))
            .andRespond(json("""{"results": [{"id": 129, "title": "Spirited Away", "release_date": "2001-07-20", "poster_path": "/sa.jpg"}]}"""))

        assertThat(client.findPoster("Spirited Away", "2002")?.releaseYear).isEqualTo("2001")
        server.verify()
    }

    @Test
    fun `finds nothing when no result has a poster`() {
        server.expect(requestTo("$API/search/movie?query=Unknown&include_adult=false"))
            .andRespond(json("""{"results": [{"id": 5, "title": "Unknown", "poster_path": ""}]}"""))

        assertThat(client.findPoster("Unknown", null)).isNull()
    }

    @Test
    fun `looks up a film by id, and reports an unknown id as no poster`() {
        server.expect(requestTo("$API/movie/329865")).andRespond(json(ARRIVAL))
        server.expect(requestTo("$API/movie/1")).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThat(client.poster(329865)?.posterPath).isEqualTo("/arrival.jpg")
        assertThat(client.poster(1)).isNull()
    }

    @Test
    fun `downloads the large poster without sending the token`() {
        server.expect(requestTo("$IMAGES/w780/arrival.jpg"))
            .andExpect { request -> assertThat(request.headers.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse() }
            .andRespond(withSuccess(byteArrayOf(1, 2, 3), MediaType.IMAGE_JPEG))

        assertThat(client.download(TmdbPoster(329865, "Arrival", "2016", "/arrival.jpg"))).containsExactly(1, 2, 3)
    }

    @Test
    fun `reports TMDB errors as TMDB_UNAVAILABLE, and a missing token before calling it`() {
        server.expect(requestTo("$API/search/movie?query=Arrival&include_adult=false")).andRespond(withServerError())

        val failed = assertThrows<GatewayException> { client.findPoster("Arrival", null) }
        assertThat(failed.code).isEqualTo(ErrorCode.TMDB_UNAVAILABLE)

        val noToken = TmdbClient(TmdbProperties(apiToken = " "), builder.build())
        assertThat(noToken.enabled).isFalse()
        assertThat(assertThrows<GatewayException> { noToken.findPoster("Arrival", null) }.code).isEqualTo(ErrorCode.FAILED_PRECONDITION)
    }

    private fun json(body: String) = withSuccess(body, MediaType.APPLICATION_JSON)

    companion object {
        const val API = "https://api.example.test/3"
        const val IMAGES = "https://images.example.test/t/p"
        const val ARRIVAL = """{"id": 329865, "title": "Arrival", "release_date": "2016-11-10", "poster_path": "/arrival.jpg"}"""
    }
}
