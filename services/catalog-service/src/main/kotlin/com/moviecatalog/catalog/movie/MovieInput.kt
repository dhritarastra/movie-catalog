package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.common.validation.IsoDate
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * A movie's editable fields, already trimmed by [normalised], with the rules Bean Validation
 * checks. The limits match the CHECK constraints in V1__create_movie.sql.
 */
data class MovieInput(
    @field:NotBlank(message = "is required")
    @field:Size(max = TITLE_MAX, message = "must be at most {max} characters")
    val title: String,

    @field:Size(max = 2000, message = "must be at most {max} characters")
    val synopsis: String?,

    @field:IsoDate(min = "1870-01-01", max = "2100-12-31")
    val releaseDate: String?,

    @field:Min(1, message = "must be between 1 and 1000")
    @field:Max(1000, message = "must be between 1 and 1000")
    val runtimeMinutes: Int?,

    @field:Size(max = 10, message = "must have at most {max} entries")
    val genres: List<@Size(max = 50, message = "each genre must be at most {max} characters") String>,
) {
    companion object {
        /** The longest title, as the CHECK constraint in V1__create_movie.sql allows. */
        const val TITLE_MAX = 200

        /** Trims text, turns blank optional text into null, and drops blank and repeated genres (ignoring case). */
        fun normalised(title: String, synopsis: String?, releaseDate: String?, runtimeMinutes: Int?, genres: List<String>) =
            MovieInput(
                title = title.trim(),
                synopsis = synopsis?.trim()?.ifEmpty { null },
                releaseDate = releaseDate?.trim()?.ifEmpty { null },
                runtimeMinutes = runtimeMinutes,
                genres = genres.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
            )
    }
}
