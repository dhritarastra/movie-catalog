package com.moviecatalog.catalog.search

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Ranks movies and people that match a search in one query. It is plain SQL rather than a
 * Spring Data repository because it spans two tables: ranking both in the database means the
 * limit never cuts off a better match from the other table.
 *
 * Both sides are lowered under the ICU collation, so case is ignored for every letter
 * ("ZOË" finds "Zoë") even in movie titles, whose column uses the database default. Accents
 * are not ignored. The query is escaped, so %, _ and \ match themselves.
 */
@Repository
class SearchRepository(private val jdbc: JdbcClient) {
    fun search(query: String, limit: Int): List<SearchMatch> =
        jdbc.sql(
            """
            SELECT kind, id FROM (
                SELECT 'MOVIE' AS kind, id, title COLLATE "und-x-icu" AS label,
                       ${rank("title")} AS rank
                FROM movie
                WHERE ${folded("title")} LIKE ${folded(":contains")} ESCAPE '\'
                UNION ALL
                SELECT 'PERSON', id, name COLLATE "und-x-icu",
                       ${rank("name")}
                FROM person
                WHERE ${folded("name")} LIKE ${folded(":contains")} ESCAPE '\'
            ) AS hits
            ORDER BY rank, label, kind, id
            LIMIT :limit
            """,
        )
            .param("query", query)
            .param("contains", "%${escape(query)}%")
            .param("prefix", "${escape(query)}%")
            .param("limit", limit)
            .query { rs, _ -> SearchMatch(SearchKind.valueOf(rs.getString("kind")), rs.getObject("id", UUID::class.java)) }
            .list()

    /** 0 for the whole title or name, 1 when it starts with the query, 2 when it only contains it. */
    private fun rank(column: String) =
        """CASE WHEN ${folded(column)} = ${folded(":query")} THEN 0
                     WHEN ${folded(column)} LIKE ${folded(":prefix")} ESCAPE '\' THEN 1
                     ELSE 2 END"""

    private fun folded(expression: String) = """lower(CAST($expression AS text) COLLATE "und-x-icu")"""

    /** Makes \, % and _ in the user's text literal inside a LIKE pattern. */
    private fun escape(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
