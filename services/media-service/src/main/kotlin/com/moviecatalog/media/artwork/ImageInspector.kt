package com.moviecatalog.media.artwork

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.ImageReader

/**
 * Trusts only the file bytes: the format comes from the file signature, the dimensions are checked
 * from the header before decoding (a small file can declare a huge image), and then the image is
 * decoded. The JPEG decoder only warns on a truncated file, so any warning counts as damage.
 */
class ImageInspector(private val maxWidth: Int, private val maxHeight: Int) {
    fun inspect(file: Path): InspectedImage {
        val format = detectFormat(file) ?: throw InvalidArtworkException("Only JPEG and PNG images are supported")
        val reader = ImageIO.getImageReadersByFormatName(format.imageIoName).next()
        val warnings = mutableListOf<String>()
        reader.addIIOReadWarningListener { _, warning -> warnings += warning }
        try {
            ImageIO.createImageInputStream(file.toFile()).use { input ->
                reader.setInput(input, true, true)
                checkDimensions(reader)
                val image = try {
                    reader.read(0)
                } catch (e: IOException) {
                    throw InvalidArtworkException("The image could not be decoded; it may be corrupt")
                }
                if (warnings.isNotEmpty()) {
                    throw InvalidArtworkException("The image is damaged or incomplete; it may be corrupt (${warnings.first()})")
                }
                return InspectedImage(format, image)
            }
        } finally {
            reader.dispose()
        }
    }

    private fun checkDimensions(reader: ImageReader) {
        val (width, height) = try {
            reader.getWidth(0) to reader.getHeight(0)
        } catch (e: IOException) {
            throw InvalidArtworkException("The image header could not be read; it may be corrupt")
        }
        if (width > maxWidth || height > maxHeight) {
            throw InvalidArtworkException("The image is ${width}x$height; the maximum is ${maxWidth}x$maxHeight")
        }
    }

    companion object {
        private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        fun detectFormat(file: Path): ImageFormat? {
            val header = Files.newInputStream(file).use { it.readNBytes(PNG_SIGNATURE.size) }
            return when {
                header.startsWith(PNG_SIGNATURE) -> ImageFormat.PNG
                header.startsWith(JPEG_SIGNATURE) -> ImageFormat.JPEG
                else -> null
            }
        }

        /** Returns null if the image is already narrow enough. */
        fun thumbnail(source: InspectedImage, width: Int): BufferedImage? {
            val image = source.image
            if (image.width <= width) return null
            val height = maxOf(1, (image.height.toLong() * width / image.width).toInt())
            val type = if (source.format == ImageFormat.PNG) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
            return BufferedImage(width, height, type).also { scaled ->
                val graphics = scaled.createGraphics()
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    graphics.drawImage(image, 0, 0, width, height, null)
                } finally {
                    graphics.dispose()
                }
            }
        }

        private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}
