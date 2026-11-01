package com.moviecatalog.catalog.credit

import com.moviecatalog.catalog.common.FieldViolation
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** [type] is null when the caller sent no role. */
data class CreditInput(
    @field:NotNull(message = "is required")
    val type: CreditType?,

    @field:Size(max = 200, message = "must be at most {max} characters")
    val characterName: String?,

    @field:Min(0, message = "must be between 0 and 1000")
    @field:Max(1000, message = "must be between 0 and 1000")
    val billingOrder: Int,
) {
    /** Rules that involve two fields, which annotations cannot check. */
    fun crossFieldViolations(): List<FieldViolation> =
        if (characterName != null && type != null && type != CreditType.CAST) {
            listOf(FieldViolation("character_name", "only cast credits have a character"))
        } else {
            emptyList()
        }

    companion object {
        fun normalised(type: CreditType?, characterName: String?, billingOrder: Int) =
            CreditInput(type, characterName?.trim()?.ifEmpty { null }, billingOrder)
    }
}
