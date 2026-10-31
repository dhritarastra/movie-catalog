package com.moviecatalog.catalog.search

import jakarta.persistence.EntityManager
import jakarta.persistence.Tuple
import org.springframework.stereotype.Repository
import java.util.Locale
import java.util.UUID

/**
 * Searches movies and people in one UNION query, so the limit applies to the combined ranking.
 * Title and name columns use an ICU collation, so lower() handles accented letters.
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

    /** Makes \, % and _ match literally in a LIKE pattern. */
    private fun escape(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        // Rank 0 is an exact match, 1 a prefix, 2 anywhere. Ties sort by text, then movies first, then id.
        // The parentheses make ORDER BY and the limit apply to the whole union.
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
