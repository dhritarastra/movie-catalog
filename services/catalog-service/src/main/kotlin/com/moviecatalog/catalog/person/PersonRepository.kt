package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.Page
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/** Plain SQL through JdbcClient, like MovieRepository. */
@Repository
class PersonRepository(private val jdbc: JdbcClient) {
    fun insert(draft: PersonDraft): Person =
        jdbc.sql(
            """
            INSERT INTO person (id, name, bio, birth_date)
            VALUES (:id, :name, :bio, :birthDate)
            RETURNING *
            """,
        )
            .param("id", UUID.randomUUID())
            .param("name", draft.name)
            .param("bio", draft.bio)
            .param("birthDate", draft.birthDate)
            .query { rs, _ -> rs.toPerson() }
            .single()

    fun findById(id: UUID): Person? =
        jdbc.sql("SELECT * FROM person WHERE id = :id")
            .param("id", id)
            .query { rs, _ -> rs.toPerson() }
            .optional()
            .orElse(null)

    /**
     * Alphabetical, ignoring case and accents, through the column's ICU collation (V2__create_person.sql).
     * The id tie-breaker keeps people with the same name in a stable order.
     */
    fun findPage(page: Page): List<Person> =
        jdbc.sql("SELECT * FROM person ORDER BY name, id LIMIT :limit OFFSET :offset")
            .param("limit", page.limit)
            .param("offset", page.offset)
            .query { rs, _ -> rs.toPerson() }
            .list()

    fun count(): Int = jdbc.sql("SELECT count(*) FROM person").query(Int::class.java).single()

    fun findVersion(id: UUID): Int? =
        jdbc.sql("SELECT version FROM person WHERE id = :id")
            .param("id", id)
            .query(Int::class.java)
            .optional()
            .orElse(null)

    /** Replaces every editable field, only if the stored version still matches. Null when no row matched. */
    fun update(id: UUID, expectedVersion: Int, draft: PersonDraft): Person? =
        jdbc.sql(
            """
            UPDATE person
            SET name = :name, bio = :bio, birth_date = :birthDate,
                version = version + 1, updated_at = now()
            WHERE id = :id AND version = :expectedVersion
            RETURNING *
            """,
        )
            .param("id", id)
            .param("expectedVersion", expectedVersion)
            .param("name", draft.name)
            .param("bio", draft.bio)
            .param("birthDate", draft.birthDate)
            .query { rs, _ -> rs.toPerson() }
            .optional()
            .orElse(null)

    /** Deletes only if the stored version still matches. False when no row matched. */
    fun delete(id: UUID, expectedVersion: Int): Boolean =
        jdbc.sql("DELETE FROM person WHERE id = :id AND version = :expectedVersion")
            .param("id", id)
            .param("expectedVersion", expectedVersion)
            .update() == 1
}

private fun ResultSet.toPerson() =
    Person(
        id = getObject("id", UUID::class.java),
        name = getString("name"),
        bio = getString("bio"),
        birthDate = getObject("birth_date", LocalDate::class.java),
        version = getInt("version"),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java).toInstant(),
    )
