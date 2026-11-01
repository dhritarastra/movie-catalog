package com.moviecatalog.media.artwork

import com.moviecatalog.media.config.MediaProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO

class ArtworkStorageTest {
    @TempDir
    lateinit var dir: Path

    private val storage by lazy { ArtworkStorage(MediaProperties(dir, 1024, 100, 100, 10, 64 * 1024)) }
    private val id = UUID.randomUUID()

    @Test
    fun `uploads start in the tmp folder, away from served files`() {
        assertThat(storage.createTempFile().parent).isEqualTo(dir.toAbsolutePath().resolve("tmp"))
    }

    @Test
    fun `files live in a folder per artwork, named by variant and format`() {
        assertThat(storage.path(id, ArtworkVariant.FULL, ImageFormat.JPEG)).isEqualTo(dir.toAbsolutePath().resolve("$id/full.jpg"))
        assertThat(storage.path(id, ArtworkVariant.THUMBNAIL, ImageFormat.PNG)).isEqualTo(dir.toAbsolutePath().resolve("$id/thumb.png"))
    }

    @Test
    fun `moves the original into place and writes the thumbnail, leaving nothing in tmp`() {
        val upload = storage.createTempFile().also { Files.write(it, TestImages.png(40, 20)) }

        storage.store(id, upload, BufferedImage(10, 5, BufferedImage.TYPE_INT_ARGB), ImageFormat.PNG)

        assertThat(upload).doesNotExist()
        assertThat(Files.readAllBytes(storage.path(id, ArtworkVariant.FULL, ImageFormat.PNG))).isEqualTo(TestImages.png(40, 20))
        assertThat(ImageIO.read(storage.path(id, ArtworkVariant.THUMBNAIL, ImageFormat.PNG).toFile()).width).isEqualTo(10)
        assertThat(Files.list(dir.resolve("tmp")).use { it.count() }).isZero()
    }

    @Test
    fun `without a thumbnail, a small original is copied as its own thumbnail`() {
        val upload = storage.createTempFile().also { Files.write(it, TestImages.png(8, 8)) }

        storage.store(id, upload, null, ImageFormat.PNG)

        assertThat(Files.readAllBytes(storage.path(id, ArtworkVariant.THUMBNAIL, ImageFormat.PNG))).isEqualTo(TestImages.png(8, 8))
    }

    @Test
    fun `delete removes every file and can be repeated`() {
        val upload = storage.createTempFile().also { Files.write(it, TestImages.png(8, 8)) }
        storage.store(id, upload, null, ImageFormat.PNG)

        storage.delete(id)
        storage.delete(id)

        assertThat(dir.resolve(id.toString())).doesNotExist()
    }
}
