package com.moviecatalog.media.artwork

class ArtworkTooLargeException(maxBytes: Long) : MediaException("The image is larger than the $maxBytes-byte limit")
