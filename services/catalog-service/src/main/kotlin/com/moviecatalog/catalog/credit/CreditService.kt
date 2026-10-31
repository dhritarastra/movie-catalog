package com.moviecatalog.catalog.credit

import com.moviecatalog.catalog.common.AlreadyExistsException
import com.moviecatalog.catalog.common.NotFoundException
import com.moviecatalog.catalog.common.validation.InputValidator
import com.moviecatalog.catalog.movie.MovieRepository
import com.moviecatalog.catalog.person.PersonRepository
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Credits have no version: an update replaces the credit as sent. */
@Service
@Transactional(readOnly = true)
class CreditService(
    private val credits: CreditRepository,
    private val movies: MovieRepository,
    private val people: PersonRepository,
    private val validator: InputValidator,
) {
    @Transactional
    fun add(movieId: UUID, personId: UUID, input: CreditInput): Credit {
        validator.check(input, input.crossFieldViolations())
        if (!movies.existsById(movieId)) throw NotFoundException("Movie $movieId not found")
        if (!people.existsById(personId)) throw NotFoundException("Person $personId not found")
        return save(Credit(movieId, personId, requireNotNull(input.type), input.characterName, input.billingOrder))
    }

    @Transactional
    fun update(id: UUID, input: CreditInput): Credit {
        validator.check(input, input.crossFieldViolations())
        val credit = credits.findByIdOrNull(id) ?: throw notFound(id)
        credit.type = requireNotNull(input.type)
        credit.characterName = input.characterName
        credit.billingOrder = input.billingOrder
        return save(credit)
    }

    @Transactional
    fun remove(id: UUID) {
        if (credits.removeById(id) == 0) throw notFound(id)
    }

    fun byMovieIds(movieIds: Collection<UUID>): Map<UUID, List<Credit>> =
        credits.findAllByMovieIdIn(movieIds, BILLING_ORDER).groupBy { it.movieId }

    fun byPersonIds(personIds: Collection<UUID>): Map<UUID, List<Credit>> =
        credits.findAllByPersonIdIn(personIds, BILLING_ORDER).groupBy { it.personId }

    /** Turns constraint errors from the database into conflict and not-found errors. */
    private fun save(credit: Credit): Credit =
        try {
            credits.saveAndFlush(credit)
        } catch (e: DataIntegrityViolationException) {
            val constraint = (e.cause as? ConstraintViolationException)?.constraintName
            when {
                constraint == "credit_unique" -> throw AlreadyExistsException("This person already has this role on this movie")
                constraint?.endsWith("_fkey") == true -> throw NotFoundException("The movie or person no longer exists")
                else -> throw e
            }
        }

    private fun notFound(id: UUID) = NotFoundException("Credit $id not found")

    private companion object {
        // Billing order, then the order they were added.
        val BILLING_ORDER: Sort = Sort.by("billingOrder", "createdAt", "id")
    }
}
