package com.moviecatalog.catalog.search

import com.moviecatalog.catalog.TestcontainersConfiguration
import com.moviecatalog.catalog.movie.Movie
import com.moviecatalog.catalog.movie.MovieRepository
import com.moviecatalog.catalog.person.Person
import com.moviecatalog.catalog.person.PersonRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

@DataJpaTest
@Import(TestcontainersConfiguration::class, SearchRepository::class)
class SearchRepositoryTest(
    @Autowired private val search: SearchRepository,
    @Autowired private val movies: MovieRepository,
    @Autowired private val people: PersonRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val titles = mutableMapOf<UUID, String>()

    @Test
    fun `the whole title or name ranks first, then one starting with the query, then one containing it, across both tables`() {
        movie("The Arrival")
        person("Arrival Smith")
        movie("Arrival")

        assertThat(labels("arrival")).containsExactly("Arrival", "Arrival Smith", "The Arrival")
    }

    @Test
    fun `equal ranks are alphabetical, ignoring case, with movies before people of the same name`() {
        person("Dune")
        movie("dune")
        movie("Dune Part Two")
        movie("Dune Messiah")

        assertThat(labels("dune")).containsExactly("dune", "Dune", "Dune Messiah", "Dune Part Two")
        assertThat(search.search("dune", 2).map { it.kind }).containsExactly(SearchKind.MOVIE, SearchKind.PERSON)
    }

    @Test
    fun `the limit applies after ranking, so a better match is never cut off`() {
        listOf("Aa Lost", "Ab Lost", "Ac Lost").forEach(::movie)
        person("Lost")

        assertThat(labels("lost", limit = 1)).containsExactly("Lost")
    }

    @Test
    fun `case is ignored for every letter, in movie titles as well as names, but accents still count`() {
        movie("Zoë Story")
        movie("Zoe Story")
        person("Zoë Ünal")

        assertThat(labels("ZOË")).containsExactly("Zoë Story", "Zoë Ünal")
        assertThat(labels("ë ü")).containsExactly("Zoë Ünal")
        assertThat(labels("zoe")).containsExactly("Zoe Story")
    }

    @Test
    fun `percent, underscore and backslash in the query match themselves`() {
        listOf("100% Love", "1000 Love", "A_B", "AxB", """AC\DC""", "ACDC").forEach(::movie)

        assertThat(labels("0%")).containsExactly("100% Love")
        assertThat(labels("_")).containsExactly("A_B")
        assertThat(labels("""\""")).containsExactly("""AC\DC""")
    }

    @Test
    fun `titles and names both use the ICU collation, so folding and order never depend on the database locale`() {
        // The columns must not rely on the database locale.
        val collations = jdbc.queryForList(
            "SELECT table_name || '.' || column_name || ' ' || collation_name FROM information_schema.columns " +
                "WHERE (table_name, column_name) IN (('movie', 'title'), ('person', 'name')) ORDER BY table_name",
            String::class.java,
        )

        assertThat(collations).containsExactly("movie.title und-x-icu", "person.name und-x-icu")
    }

    @Test
    fun `nothing matching gives an empty list`() {
        movie("Arrival")

        assertThat(search.search("zzz", 10)).isEmpty()
    }

    private fun labels(query: String, limit: Int = 10): List<String> = search.search(query, limit).map { titles.getValue(it.id) }

    private fun movie(title: String) {
        titles[movies.saveAndFlush(Movie(title, null, null, null, emptyList())).id!!] = title
    }

    private fun person(name: String) {
        titles[people.saveAndFlush(Person(name, null, null)).id!!] = name
    }
}
