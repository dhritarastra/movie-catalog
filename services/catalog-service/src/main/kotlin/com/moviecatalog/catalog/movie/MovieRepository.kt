package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.common.Page
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/** Plain SQL through JdbcClient, so every query the catalog runs is visible here. */
@Repository
class MovieRepository(private val jdbc: JdbcClient) {
    fun insert(draft: MovieDraft): Movie =
        jdbc.sql(
            """
            INSERT INTO movie (id, title, synopsis, release_date, runtime_minutes, genres)
            VALUES (:id, :title, :synopsis, :releaseDate, :runtimeMinutes, :genres)
            RETURNING *
            """,
        )
            .param("id", UUID.randomUUID())
            .param("title", draft.title)
            .param("synopsis", draft.synopsis)
            .param("releaseDate", draft.releaseDate)
            .param("runtimeMinutes", draft.runtimeMinutes)
            .param("genres", draft.genres.toTypedArray())
            .query { rs, _ -> rs.toMovie() }
            .single()

    fun findById(id: UUID): Movie? =
        jdbc.sql("SELECT * FROM movie WHERE id = :id")
            .param("id", id)
            .query { rs, _ -> rs.toMovie() }
            .optional()
            .orElse(null)

    /** Newest first; the id tie-breaker keeps the order stable across pages. */
    fun findPage(page: Page): List<Movie> =
        jdbc.sql("SELECT * FROM movie ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
            .param("limit", page.limit)
            .param("offset", page.offset)
            .query { rs, _ -> rs.toMovie() }
            .list()

    fun count(): Int = jdbc.sql("SELECT count(*) FROM movie").query(Int::class.java).single()

    /** Locks the row for the rest of the transaction, so its version and artwork cannot change underneath. */
    fun lockArtworkState(id: UUID): ArtworkState? =
        jdbc.sql("SELECT version, artwork_id FROM movie WHERE id = :id FOR UPDATE")
            .param("id", id)
            .query { rs, _ -> ArtworkState(rs.getInt("version"), rs.getObject("artwork_id", UUID::class.java)) }
            .optional()
            .orElse(null)

    fun updateArtwork(id: UUID, artworkId: UUID?): Movie =
        jdbc.sql(
            """
            UPDATE movie SET artwork_id = :artworkId, version = version + 1, updated_at = now()
            WHERE id = :id
            RETURNING *
            """,
        )
            .param("id", id)
            .param("artworkId", artworkId)
            .query { rs, _ -> rs.toMovie() }
            .single()

    data class ArtworkState(val version: Int, val artworkId: UUID?)
}

private fun ResultSet.toMovie() =
    Movie(
        id = getObject("id", UUID::class.java),
        title = getString("title"),
        synopsis = getString("synopsis"),
        releaseDate = getObject("release_date", LocalDate::class.java),
        runtimeMinutes = getInt("runtime_minutes").takeUnless { wasNull() },
        genres = getStringList("genres"),
        artworkId = getObject("artwork_id", UUID::class.java),
        version = getInt("version"),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java).toInstant(),
    )

/** Postgres text[] columns come back as a java.sql.Array of strings. */
@Suppress("UNCHECKED_CAST")
private fun ResultSet.getStringList(column: String): List<String> = (getArray(column).array as Array<String>).toList()
