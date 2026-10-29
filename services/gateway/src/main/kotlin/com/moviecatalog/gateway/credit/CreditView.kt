package com.moviecatalog.gateway.credit

import com.moviecatalog.catalog.v1.Credit as CreditProto
import com.moviecatalog.catalog.v1.CreditType as CreditTypeProto

/**
 * The GraphQL `Credit` as data. The movie and person are resolved by CreditController's batch
 * mappings from [movieId] and [personId], so a page of credits costs one call for each, not one per credit.
 */
data class CreditView(
    val id: String,
    val type: CreditType,
    val characterName: String?,
    val billingOrder: Int,
    val movieId: String,
    val personId: String,
) {
    companion object {
        fun of(credit: CreditProto) =
            CreditView(
                id = credit.id,
                type = credit.type.toView(),
                characterName = if (credit.hasCharacterName()) credit.characterName else null,
                billingOrder = credit.billingOrder,
                movieId = credit.movieId,
                personId = credit.personId,
            )
    }
}

private fun CreditTypeProto.toView(): CreditType =
    when (this) {
        CreditTypeProto.CREDIT_TYPE_CAST -> CreditType.CAST
        CreditTypeProto.CREDIT_TYPE_DIRECTOR -> CreditType.DIRECTOR
        CreditTypeProto.CREDIT_TYPE_WRITER -> CreditType.WRITER
        CreditTypeProto.CREDIT_TYPE_PRODUCER -> CreditType.PRODUCER
        // The catalog never stores a credit without a role.
        CreditTypeProto.CREDIT_TYPE_UNSPECIFIED, CreditTypeProto.UNRECOGNIZED -> error("A credit from the catalog has no role")
    }
