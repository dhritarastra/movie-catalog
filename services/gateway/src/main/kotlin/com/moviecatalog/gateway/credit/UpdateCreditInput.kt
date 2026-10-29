package com.moviecatalog.gateway.credit

/** `UpdateCreditInput` as received: the role, character and billing order replace the credit's own. */
data class UpdateCreditInput(val type: CreditType, val characterName: String?, val billingOrder: Int)
