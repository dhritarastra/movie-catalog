package com.moviecatalog.gateway.tmdb

import com.moviecatalog.gateway.graphql.ErrorCode
import com.moviecatalog.gateway.graphql.GatewayException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode

/** Finds and downloads film posters from TMDB (themoviedb.org). */
@Component
class TmdbClient(private val properties: TmdbProperties, @Qualifier("tmdbRestClient") private val http: RestClient) {
    val enabled: Boolean get() = properties.apiToken.isNotBlank()

    /** Searches by title and year, then by title alone, since release years differ between countries. */
    fun findPoster(title: String, year: String?): TmdbPoster? {
        val byYear = year?.let { search(title, it) }
        return byYear ?: search(title, null)
    }

    /** Null if TMDB has no such film, or no poster for it. */
    fun poster(tmdbId: Int): TmdbPoster? =
        try {
            toPoster(get("${properties.apiUrl}/movie/$tmdbId"))
        } catch (e: HttpClientErrorException.NotFound) {
            null
        }

    fun download(poster: TmdbPoster): ByteArray =
        call { http.get().uri("${properties.imageUrl}/w780${poster.posterPath}").retrieve().body(ByteArray::class.java) }
            ?: throw unavailable()

    fun previewUrl(poster: TmdbPoster): String = "${properties.imageUrl}/w342${poster.posterPath}"

    private fun search(title: String, year: String?): TmdbPoster? {
        val results = get(
            "${properties.apiUrl}/search/movie?query={title}&include_adult=false" + (year?.let { "&year={year}" } ?: ""),
            title,
            year,
        ).path("results")
        return results.firstNotNullOfOrNull { toPoster(it) }
    }

    private fun get(url: String, vararg variables: String?): JsonNode {
        if (!enabled) throw GatewayException(ErrorCode.FAILED_PRECONDITION, "Poster lookup is not set up on this server")
        return call {
            http.get().uri(url, *variables.filterNotNull().toTypedArray())
                .header("Authorization", "Bearer ${properties.apiToken}")
                .retrieve()
                .body(JsonNode::class.java)
        } ?: throw unavailable()
    }

    private fun toPoster(movie: JsonNode): TmdbPoster? {
        val path = movie.path("poster_path").asString(null)?.takeIf { it.isNotBlank() } ?: return null
        val year = movie.path("release_date").asString("").take(4).ifBlank { null }
        return TmdbPoster(movie.path("id").asInt(), movie.path("title").asString(""), year, path)
    }

    private fun <T> call(request: () -> T): T =
        try {
            request()
        } catch (e: HttpClientErrorException.NotFound) {
            throw e
        } catch (e: RestClientException) {
            throw unavailable()
        }

    private fun unavailable() = GatewayException(ErrorCode.TMDB_UNAVAILABLE, "TMDB could not be reached. Try again, or choose an image.")
}
