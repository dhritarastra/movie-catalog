package com.moviecatalog.media.artwork

/** The request or the file itself is unacceptable: wrong type, corrupt, too many pixels, bad ordering. */
class InvalidArtworkException(message: String) : MediaException(message)
