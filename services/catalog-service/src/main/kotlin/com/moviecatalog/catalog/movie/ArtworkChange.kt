package com.moviecatalog.catalog.movie

import java.util.UUID

data class ArtworkChange(val movie: Movie, val previousArtworkId: UUID?)
