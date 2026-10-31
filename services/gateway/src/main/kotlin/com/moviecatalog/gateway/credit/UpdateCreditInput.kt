package com.moviecatalog.gateway.credit

data class UpdateCreditInput(val type: CreditType, val characterName: String?, val billingOrder: Int)
