package com.moviecatalog.media.artwork

import com.moviecatalog.media.config.MediaProperties
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Service
class ArtworkService(
    private val storage: ArtworkStorage,
    private val repository: ArtworkRepository,
    private val properties: MediaProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val inspector = ImageInspector(properties.maxWidth, properties.maxHeight)

    /**
     * Validates a fully received upload and stores it. Files are moved into place before the row is
     * written, so a row never points at missing files; if the insert fails the files are removed.
     * The temporary upload is always deleted.
     */
    fun store(upload: Path, sizeBytes: Long, sha256: String): Artwork {
        try {
            val inspected = inspector.inspect(upload)
            val id = UUID.randomUUID()
            storage.store(id, upload, ImageInspector.thumbnail(inspected, properties.thumbnailWidth), inspected.format)
            try {
                // save() merges because the id is assigned, so Hibernate first checks that no such row exists.
                return repository.save(Artwork(id, inspected.format, sizeBytes, inspected.image.width, inspected.image.height, sha256))
            } catch (e: RuntimeException) {
                storage.delete(id)
                throw e
            }
        } finally {
            Files.deleteIfExists(upload)
        }
    }

    fun open(id: UUID, variant: ArtworkVariant): StoredFile {
        val artwork = repository.findByIdOrNull(id) ?: throw ArtworkNotFoundException("Artwork $id not found")
        val path = storage.path(id, variant, artwork.format)
        if (!Files.exists(path)) {
            log.error("Artwork {} has a metadata row but no {} file at {}", id, variant, path)
            throw ArtworkNotFoundException("Artwork $id not found")
        }
        return StoredFile(artwork, path, Files.size(path))
    }

    /** Safe to repeat: deleting missing artwork succeeds and reports that nothing existed. */
    fun delete(id: UUID): Boolean {
        val existed = repository.removeById(id) > 0
        storage.delete(id)
        return existed
    }
}
