package com.moviecatalog.media.artwork

/** The two files kept per artwork, named `<fileName>.<extension>` in its folder. */
enum class ArtworkVariant(val fileName: String) { FULL("full"), THUMBNAIL("thumb") }
