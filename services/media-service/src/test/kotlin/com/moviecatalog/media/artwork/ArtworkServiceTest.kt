package com.moviecatalog.media.artwork

import com.moviecatalog.media.config.MediaProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.UUID
import javax.imageio.ImageIO

class ArtworkServiceTest {
    @TempDir
    lateinit var dir: Path

    private val repository = mockk<ArtworkRepository>()
    private lateinit var storage: ArtworkStorage
    private lateinit var service: ArtworkService

    @BeforeEach
    fun setUp() {
        val properties = MediaProperties(dir, 1_000_000, 100, 100, 10, 64 * 1024)
        storage = ArtworkStorage(properties)
        service = ArtworkService(storage, repository, properties)
        every { repository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `stores a valid image with its dimensions and hash, and a thumbnail at the configured width`() {
        val saved = slot<Artwork>()
        every { repository.save(capture(saved)) } answers { firstArg() }

        val artwork = service.store(upload(TestImages.png(40, 20)), 123, "hash")

        assertThat(saved.captured).isSameAs(artwork)
        assertThat(artwork.format).isEqualTo(ImageFormat.PNG)
        assertThat(artwork.width to artwork.height).isEqualTo(40 to 20)
        assertThat(artwork.sizeBytes).isEqualTo(123)
        assertThat(artwork.sha256).isEqualTo("hash")
        val thumbnail = ImageIO.read(storage.path(artwork.id, ArtworkVariant.THUMBNAIL, ImageFormat.PNG).toFile())
        assertThat(thumbnail.width to thumbnail.height).isEqualTo(10 to 5)
    }

    @Test
    fun `an unacceptable file stores nothing and the upload is deleted`() {
        val upload = upload(TestImages.svg)

        assertThrows<InvalidArtworkException> { service.store(upload, TestImages.svg.size.toLong(), "hash") }

        assertThat(upload).doesNotExist()
        verify(exactly = 0) { repository.save(any()) }
        assertThat(Files.list(dir).use { files -> files.filter { it.fileName.toString() != "tmp" }.count() }).isZero()
    }

    @Test
    fun `if the row cannot be written, the stored files are removed again`() {
        every { repository.save(any()) } throws IllegalStateException("database down")
        val upload = upload(TestImages.png(8, 8))

        assertThrows<IllegalStateException> { service.store(upload, 1, "hash") }

        assertThat(upload).doesNotExist()
        assertThat(Files.list(dir).use { files -> files.filter { it.fileName.toString() != "tmp" }.count() }).isZero()
    }

    @Test
    fun `opens a stored variant with its size`() {
        val artwork = service.store(upload(TestImages.png(8, 8)), 1, "hash")
        every { repository.findById(artwork.id) } returns Optional.of(artwork)

        val file = service.open(artwork.id, ArtworkVariant.FULL)

        assertThat(file.sizeBytes).isEqualTo(Files.size(file.path))
        assertThat(file.artwork).isSameAs(artwork)
    }

    @Test
    fun `unknown artwork, and a row whose file is missing, are both not found`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns Optional.empty()
        assertThrows<ArtworkNotFoundException> { service.open(id, ArtworkVariant.FULL) }

        every { repository.findById(id) } returns Optional.of(Artwork(id, ImageFormat.PNG, 1, 1, 1, "hash"))
        assertThrows<ArtworkNotFoundException> { service.open(id, ArtworkVariant.THUMBNAIL) }
    }

    @Test
    fun `delete removes the row and the files, and reports whether anything existed`() {
        val artwork = service.store(upload(TestImages.png(8, 8)), 1, "hash")
        every { repository.removeById(artwork.id) } returnsMany listOf(1, 0)

        assertThat(service.delete(artwork.id)).isTrue()
        assertThat(dir.resolve(artwork.id.toString())).doesNotExist()
        assertThat(service.delete(artwork.id)).isFalse()
    }

    private fun upload(bytes: ByteArray): Path = storage.createTempFile().also { Files.write(it, bytes) }
}
