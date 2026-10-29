package com.moviecatalog.catalog.credit

import com.moviecatalog.catalog.common.RequestArguments
import com.moviecatalog.catalog.common.complete
import com.moviecatalog.catalog.v1.AddCreditRequest
import com.moviecatalog.catalog.v1.CreditList
import com.moviecatalog.catalog.v1.CreditServiceGrpc
import com.moviecatalog.catalog.v1.CreditsByKeyResponse
import com.moviecatalog.catalog.v1.GetCreditsByMovieIdsRequest
import com.moviecatalog.catalog.v1.GetCreditsByPersonIdsRequest
import com.moviecatalog.catalog.v1.RemoveCreditRequest
import com.moviecatalog.catalog.v1.RemoveCreditResponse
import com.moviecatalog.catalog.v1.UpdateCreditRequest
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service
import java.util.UUID
import com.moviecatalog.catalog.v1.Credit as CreditProto
import com.moviecatalog.catalog.v1.CreditType as CreditTypeProto

/** gRPC endpoint for credits, shaped like MovieGrpcService. */
@Service
class CreditGrpcService(private val credits: CreditService) : CreditServiceGrpc.CreditServiceImplBase() {
    override fun addCredit(request: AddCreditRequest, responseObserver: StreamObserver<CreditProto>) =
        responseObserver.complete(
            credits.add(
                movieId = RequestArguments.uuid("movie_id", request.movieId),
                personId = RequestArguments.uuid("person_id", request.personId),
                input = CreditInput.normalised(
                    request.type.toDomain(),
                    if (request.hasCharacterName()) request.characterName else null,
                    request.billingOrder,
                ),
            ).toProto(),
        )

    override fun updateCredit(request: UpdateCreditRequest, responseObserver: StreamObserver<CreditProto>) =
        responseObserver.complete(
            credits.update(
                id = RequestArguments.uuid("id", request.id),
                input = CreditInput.normalised(
                    request.type.toDomain(),
                    if (request.hasCharacterName()) request.characterName else null,
                    request.billingOrder,
                ),
            ).toProto(),
        )

    override fun removeCredit(request: RemoveCreditRequest, responseObserver: StreamObserver<RemoveCreditResponse>) {
        val id = RequestArguments.uuid("id", request.id)
        credits.remove(id)
        responseObserver.complete(RemoveCreditResponse.newBuilder().setId(id.toString()).build())
    }

    override fun getCreditsByMovieIds(request: GetCreditsByMovieIdsRequest, responseObserver: StreamObserver<CreditsByKeyResponse>) {
        val ids = request.movieIdsList.map { RequestArguments.uuid("movie_ids", it) }
        responseObserver.complete(byKey(ids, credits.byMovieIds(ids)))
    }

    override fun getCreditsByPersonIds(request: GetCreditsByPersonIdsRequest, responseObserver: StreamObserver<CreditsByKeyResponse>) {
        val ids = request.personIdsList.map { RequestArguments.uuid("person_ids", it) }
        responseObserver.complete(byKey(ids, credits.byPersonIds(ids)))
    }

    /** Every requested ID gets an entry, empty when it has no credits, as the contract promises. */
    private fun byKey(ids: List<UUID>, found: Map<UUID, List<Credit>>): CreditsByKeyResponse =
        CreditsByKeyResponse.newBuilder()
            .putAllCredits(
                ids.associate { id ->
                    id.toString() to CreditList.newBuilder().addAllCredits(found[id].orEmpty().map { it.toProto() }).build()
                },
            )
            .build()
}

/** CREDIT_TYPE_UNSPECIFIED (an omitted role) and unknown numbers become null, which validation rejects. */
private fun CreditTypeProto.toDomain(): CreditType? =
    when (this) {
        CreditTypeProto.CREDIT_TYPE_CAST -> CreditType.CAST
        CreditTypeProto.CREDIT_TYPE_DIRECTOR -> CreditType.DIRECTOR
        CreditTypeProto.CREDIT_TYPE_WRITER -> CreditType.WRITER
        CreditTypeProto.CREDIT_TYPE_PRODUCER -> CreditType.PRODUCER
        CreditTypeProto.CREDIT_TYPE_UNSPECIFIED, CreditTypeProto.UNRECOGNIZED -> null
    }

private fun CreditType.toProto(): CreditTypeProto =
    when (this) {
        CreditType.CAST -> CreditTypeProto.CREDIT_TYPE_CAST
        CreditType.DIRECTOR -> CreditTypeProto.CREDIT_TYPE_DIRECTOR
        CreditType.WRITER -> CreditTypeProto.CREDIT_TYPE_WRITER
        CreditType.PRODUCER -> CreditTypeProto.CREDIT_TYPE_PRODUCER
    }

private fun Credit.toProto(): CreditProto =
    CreditProto.newBuilder()
        .setId(requireNotNull(id) { "a saved credit has an id" }.toString())
        .setMovieId(movieId.toString())
        .setPersonId(personId.toString())
        .setType(type.toProto())
        .setBillingOrder(billingOrder)
        .apply { this@toProto.characterName?.let { setCharacterName(it) } }
        .build()
