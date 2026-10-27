package com.moviecatalog.media.artwork

import java.nio.file.Path

/** A stored file ready to stream: the metadata row plus the path of the requested variant. */
class StoredFile(val artwork: Artwork, val path: Path, val sizeBytes: Long)
