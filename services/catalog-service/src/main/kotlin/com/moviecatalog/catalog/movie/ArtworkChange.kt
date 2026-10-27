package com.moviecatalog.catalog.movie

import java.util.UUID

/** Result of attaching or removing artwork: the updated movie and the artwork it replaced, if any. */
data class ArtworkChange(val movie: Movie, val previousArtworkId: UUID?)
