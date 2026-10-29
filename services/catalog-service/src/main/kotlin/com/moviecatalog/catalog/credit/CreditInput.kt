package com.moviecatalog.catalog.credit

import com.moviecatalog.catalog.common.FieldViolation
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * A credit's editable fields, already trimmed by [normalised], with the rules Bean Validation
 * checks. The limits match the CHECK constraints in V3__create_credit.sql. [type] is null when
 * the caller sent no role (proto3's CREDIT_TYPE_UNSPECIFIED).
 */
data class CreditInput(
    @field:NotNull(message = "is required")
    val type: CreditType?,

    @field:Size(max = 200, message = "must be at most {max} characters")
    val characterName: String?,

    @field:Min(0, message = "must be between 0 and 1000")
    @field:Max(1000, message = "must be between 0 and 1000")
    val billingOrder: Int,
) {
    /**
     * The rule that involves two fields, which a field annotation cannot see. InputValidator
     * reports it together with the annotation rules.
     */
    fun crossFieldViolations(): List<FieldViolation> =
        if (characterName != null && type != null && type != CreditType.CAST) {
            listOf(FieldViolation("character_name", "only cast credits have a character"))
        } else {
            emptyList()
        }

    companion object {
        /** Trims the character name and turns a blank one into null. */
        fun normalised(type: CreditType?, characterName: String?, billingOrder: Int) =
            CreditInput(type, characterName?.trim()?.ifEmpty { null }, billingOrder)
    }
}
