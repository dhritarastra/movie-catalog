package com.moviecatalog.media.artwork

import org.springframework.stereotype.Component
import com.moviecatalog.media.config.MediaProperties
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO

/** Files live at <storage-dir>/<id>/full.<ext> and thumb.<ext>; uploads wait in tmp until validated. */
@Component
class ArtworkStorage(properties: MediaProperties) {
    private val root: Path = properties.storageDir.toAbsolutePath()
    private val tmp: Path = Files.createDirectories(root.resolve("tmp"))

    fun createTempFile(): Path = Files.createTempFile(tmp, "upload-", ".part")

    fun path(id: UUID, variant: ArtworkVariant, format: ImageFormat): Path =
        root.resolve(id.toString()).resolve("${variant.fileName}.${format.extension}")

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

    /** Missing files are not an error, so deletes can be repeated. */
    fun delete(id: UUID) {
        val dir = root.resolve(id.toString())
        if (!Files.exists(dir)) return
        Files.list(dir).use { files -> files.forEach(Files::deleteIfExists) }
        Files.deleteIfExists(dir)
    }
}
