package com.moviecatalog.media.artwork

import org.springframework.stereotype.Component
import com.moviecatalog.media.config.MediaProperties
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Image files on disk: `<storage-dir>/<artwork id>/full.<ext>` and `thumb.<ext>`. Uploads are
 * written to `<storage-dir>/tmp` first and moved into place only once they have passed
 * validation, so a half-written upload is never served.
 */
@Component
class ArtworkStorage(properties: MediaProperties) {
    private val root: Path = properties.storageDir.toAbsolutePath()
    private val tmp: Path = Files.createDirectories(root.resolve("tmp"))

    fun createTempFile(): Path = Files.createTempFile(tmp, "upload-", ".part")

    fun path(id: UUID, variant: ArtworkVariant, format: ImageFormat): Path =
        root.resolve(id.toString()).resolve("${variant.fileName}.${format.extension}")

    /** Moves the validated original into place and writes the thumbnail (or a copy of a small original). */
    fun store(id: UUID, original: Path, thumbnail: BufferedImage?, format: ImageFormat) {
        Files.createDirectories(root.resolve(id.toString()))
        val thumbPath = path(id, ArtworkVariant.THUMBNAIL, format)
        if (thumbnail == null) {
            Files.copy(original, thumbPath, StandardCopyOption.REPLACE_EXISTING)
        } else {
            val tmpThumb = createTempFile()
            try {
                ImageIO.write(thumbnail, format.imageIoName, tmpThumb.toFile())
                Files.move(tmpThumb, thumbPath, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(tmpThumb)
            }
        }
        Files.move(original, path(id, ArtworkVariant.FULL, format), StandardCopyOption.REPLACE_EXISTING)
    }

    /** Removes every file for [id]. Missing files are not an error, so deletes can be repeated. */
    fun delete(id: UUID) {
        val dir = root.resolve(id.toString())
        if (!Files.exists(dir)) return
        Files.list(dir).use { files -> files.forEach(Files::deleteIfExists) }
        Files.deleteIfExists(dir)
    }
}
