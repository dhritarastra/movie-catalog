package com.moviecatalog.gateway.credit

import com.moviecatalog.gateway.grpc.CatalogClient
import com.moviecatalog.gateway.movie.MovieView
import com.moviecatalog.gateway.person.PersonView
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller

/** Nested credit fields are batch mappings: one catalog call per field for a whole page. */
@Controller
class CreditController(private val catalog: CatalogClient) {
    @MutationMapping
    fun addCredit(@Argument input: AddCreditInput): CreditView =
        CreditView.of(catalog.addCredit(input.movieId, input.personId, input.type, input.characterName, input.billingOrder))

    @MutationMapping
    fun updateCredit(@Argument id: String, @Argument input: UpdateCreditInput): CreditView =
        CreditView.of(catalog.updateCredit(id, input.type, input.characterName, input.billingOrder))

    @MutationMapping
    fun removeCredit(@Argument id: String): String = catalog.removeCredit(id)

    // cast and creators each make one call. Sharing one load would need a DataLoader.
    @BatchMapping(typeName = "Movie", field = "cast")
    fun cast(movies: List<MovieView>): Map<MovieView, List<CreditView>> = creditsOf(movies) { it.type == CreditType.CAST }

    @BatchMapping(typeName = "Movie", field = "creators")
    fun creators(movies: List<MovieView>): Map<MovieView, List<CreditView>> = creditsOf(movies) { it.type != CreditType.CAST }

    @BatchMapping(typeName = "Person", field = "credits")
    fun credits(people: List<PersonView>): Map<PersonView, List<CreditView>> {
        val byPerson = catalog.creditsByPersonIds(people.map { it.id })
        return people.associateWith { person -> byPerson[person.id].orEmpty().map(CreditView::of) }
    }

    /** Null if the movie was deleted after the credits were loaded, so the rest of the response still works. */
    @BatchMapping(typeName = "Credit", field = "movie")
    fun movie(credits: List<CreditView>): Map<CreditView, MovieView> {
        val movies = catalog.moviesByIds(credits.map { it.movieId }.distinct())
        return credits.mapNotNull { credit -> movies[credit.movieId]?.let { credit to MovieView.of(it) } }.toMap()
    }

    @BatchMapping(typeName = "Credit", field = "person")
    fun person(credits: List<CreditView>): Map<CreditView, PersonView> {
        val people = catalog.peopleByIds(credits.map { it.personId }.distinct())
        return credits.mapNotNull { credit -> people[credit.personId]?.let { credit to PersonView.of(it) } }.toMap()
    }

    private fun creditsOf(movies: List<MovieView>, keep: (CreditView) -> Boolean): Map<MovieView, List<CreditView>> {
        val byMovie = catalog.creditsByMovieIds(movies.map { it.id })
        return movies.associateWith { movie -> byMovie[movie.id].orEmpty().map(CreditView::of).filter(keep) }
    }
}
