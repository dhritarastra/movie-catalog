package com.moviecatalog.gateway.credit

data class AddCreditInput(
    val movieId: String,
    val personId: String,
    val type: CreditType,
    val characterName: String?,
    val billingOrder: Int,
)
