package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.validation.IsoDate
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Limits match the CHECK constraints on the person table. Names don't have to be unique. */
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
        const val NAME_MAX = 200

        fun normalised(name: String, bio: String?, birthDate: String?) =
            PersonInput(
                name = name.trim(),
                bio = bio?.trim()?.ifEmpty { null },
                birthDate = birthDate?.trim()?.ifEmpty { null },
            )
    }
}
