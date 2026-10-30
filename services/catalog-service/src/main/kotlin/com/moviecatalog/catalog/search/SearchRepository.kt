package com.moviecatalog.catalog.search

import jakarta.persistence.EntityManager
import jakarta.persistence.Tuple
import org.springframework.stereotype.Repository
import java.util.Locale
import java.util.UUID

/**
 * Ranks movies and people that match a search in one JPQL query over both entities. The
 * UNION ALL (JPQL since Jakarta Persistence 3.2) lets the database rank both tables together,
 * so the limit never cuts off a better match from the other table. It uses the EntityManager
 * rather than a Spring Data repository because a Spring Data repository belongs to one entity,
 * and this query spans two.
 *
 * Titles and names both use the ICU collation (V2 and V4 migrations), so lower() folds every
 * letter and ties sort alike in both tables. That is set on the columns because standard JPQL
 * has no COLLATE, and Hibernate's collate() would add a Hibernate-only function. The query is
 * lowered here, with Unicode rules, and escaped, so %, _ and \ match themselves. Accents are
 * not ignored.
 */
@Repository
class SearchRepository(private val entityManager: EntityManager) {
    fun search(query: String, limit: Int): List<SearchMatch> {
        val text = query.lowercase(Locale.ROOT)
        return entityManager.createQuery(SEARCH, Tuple::class.java)
            .setParameter("query", text)
            .setParameter("prefix", "${escape(text)}%")
            .setParameter("contains", "%${escape(text)}%")
            .setMaxResults(limit)
            .resultList
            .map { SearchMatch(SearchKind.valueOf(it.get("kind", String::class.java)), it.get("id", UUID::class.java)) }
    }

    /** Makes \, % and _ in the user's text literal inside a LIKE pattern. */
    private fun escape(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        // Rank 0 is the whole title or name, 1 one that starts with the query, 2 one that only
        // contains it. Equal ranks are alphabetical, then movies first, then by ID, so the
        // order is stable. The parentheses make ORDER BY (and the limit) apply to the whole
        // union; without them Hibernate orders only the second SELECT.
        const val SEARCH = """
            (SELECT 'MOVIE' AS kind, m.id AS id, m.title AS label,
                   CASE WHEN lower(m.title) = :query THEN 0
                        WHEN lower(m.title) LIKE :prefix ESCAPE '\' THEN 1
                        ELSE 2 END AS rank
            FROM Movie m
            WHERE lower(m.title) LIKE :contains ESCAPE '\'
            UNION ALL
            SELECT 'PERSON', p.id, p.name,
                   CASE WHEN lower(p.name) = :query THEN 0
                        WHEN lower(p.name) LIKE :prefix ESCAPE '\' THEN 1
                        ELSE 2 END
            FROM Person p
            WHERE lower(p.name) LIKE :contains ESCAPE '\')
            ORDER BY rank, label, kind, id
            """
    }
}
