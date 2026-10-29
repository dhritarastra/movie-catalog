package com.moviecatalog.gateway.credit

/** `AddCreditInput` as received; the catalog validates and normalises it. */
data class AddCreditInput(
    val movieId: String,
    val personId: String,
    val type: CreditType,
    val characterName: String?,
    val billingOrder: Int,
)
