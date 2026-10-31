package com.moviecatalog.media.artwork

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ImageInspectorTest {
    @TempDir
    lateinit var dir: Path

    private val inspector = ImageInspector(maxWidth = 2000, maxHeight = 2000)

    @Test
    fun `accepts PNG and JPEG by their contents`() {
        assertThat(inspector.inspect(file(TestImages.png(30, 20))).format).isEqualTo(ImageFormat.PNG)
        assertThat(inspector.inspect(file(TestImages.jpeg(30, 20))).format).isEqualTo(ImageFormat.JPEG)
    }

    @Test
    fun `ignores the file name, so a renamed SVG is still rejected`() {
        val error = assertThrows<InvalidArtworkException> { inspector.inspect(file(TestImages.svg, "poster.png")) }

        assertThat(error.message).contains("Only JPEG and PNG")
    }

    @Test
    fun `rejects GIFs, plain text and empty files`() {
        listOf("GIF89a....".toByteArray(), "hello".toByteArray(), ByteArray(0)).forEach {
            assertThrows<InvalidArtworkException> { inspector.inspect(file(it)) }
        }
    }

    @Test
    fun `rejects huge declared dimensions from the header without decoding the pixels`() {
        // Would need about 40 GB to decode; the file is 33 bytes.
        val bomb = file(TestImages.pngHeaderClaiming(100_000, 100_000))

        val error = assertThrows<InvalidArtworkException> { inspector.inspect(bomb) }

        assertThat(Files.size(bomb)).isLessThan(100)
        assertThat(error.message).isEqualTo("The image is 100000x100000; the maximum is 2000x2000")
    }

    @Test
    fun `accepts dimensions exactly at the limit and rejects one pixel more`() {
        inspector.inspect(file(TestImages.png(2000, 1)))

        assertThrows<InvalidArtworkException> { inspector.inspect(file(TestImages.png(2001, 1))) }
    }

    @Test
    fun `rejects a valid header followed by missing image data`() {
        val error = assertThrows<InvalidArtworkException> { inspector.inspect(file(TestImages.pngHeaderClaiming(10, 10))) }

        assertThat(error.message).contains("could not be decoded")
    }

    @Test
    fun `rejects a truncated PNG`() {
        val png = TestImages.png(200, 200, noise = true)

        assertThrows<InvalidArtworkException> { inspector.inspect(file(png.copyOf(png.size / 2))) }
    }

    @Test
    fun `rejects truncated JPEGs, which the decoder would otherwise fill in with grey`() {
        val jpeg = TestImages.jpeg(300, 300, noise = true)

        listOf(jpeg.size / 2, jpeg.size - 2).forEach { length ->
            val error = assertThrows<InvalidArtworkException> { inspector.inspect(file(jpeg.copyOf(length))) }
            assertThat(error.message).describedAs("cut to $length of ${jpeg.size} bytes").contains("corrupt")
        }
        inspector.inspect(file(jpeg))
    }

    @Test
    fun `thumbnails wide images to the target width keeping the aspect ratio`() {
        val wide = inspector.inspect(file(TestImages.png(1000, 500)))

        val thumbnail = ImageInspector.thumbnail(wide, 400)!!

        assertThat(thumbnail.width).isEqualTo(400)
        assertThat(thumbnail.height).isEqualTo(200)
    }

    @Test
    fun `does not upscale images already narrower than the thumbnail`() {
        assertThat(ImageInspector.thumbnail(inspector.inspect(file(TestImages.png(300, 100))), 400)).isNull()
    }

    private fun file(bytes: ByteArray, name: String = "upload.bin"): Path = dir.resolve(name).also { Files.write(it, bytes) }
}
