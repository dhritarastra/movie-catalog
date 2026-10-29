package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.validation.IsoDate
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * A person's editable fields, already trimmed by [normalised], with the rules Bean Validation
 * checks. The limits match the CHECK constraints in V2__create_person.sql. Names are not unique.
 */
data class PersonInput(
    @field:NotBlank(message = "is required")
    @field:Size(max = NAME_MAX, message = "must be at most {max} characters")
    val name: String,

    @field:Size(max = 2000, message = "must be at most {max} characters")
    val bio: String?,

    @field:IsoDate(min = "1800-01-01", notInFuture = true)
    val birthDate: String?,
) {
    companion object {
        /** The longest name, as the CHECK constraint in V2__create_person.sql allows. */
        const val NAME_MAX = 200

        /** Trims text and turns blank optional text into null. */
        fun normalised(name: String, bio: String?, birthDate: String?) =
            PersonInput(
                name = name.trim(),
                bio = bio?.trim()?.ifEmpty { null },
                birthDate = birthDate?.trim()?.ifEmpty { null },
            )
    }
}
