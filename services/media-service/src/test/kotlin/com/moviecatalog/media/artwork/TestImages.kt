package com.moviecatalog.media.artwork

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Random
import java.util.zip.CRC32
import javax.imageio.ImageIO

/** Image fixtures generated in code, so tests need no binary files and every edge case is explicit. */
object TestImages {
    fun png(width: Int, height: Int, noise: Boolean = false): ByteArray = encode(image(width, height, noise, BufferedImage.TYPE_INT_ARGB), "png")

    fun jpeg(width: Int, height: Int): ByteArray = encode(image(width, height, false, BufferedImage.TYPE_INT_RGB), "jpeg")

    /**
     * A tiny file whose PNG header claims [width] x [height]. Decoding it would try to allocate
     * width * height pixels, which is why dimensions must be checked before decoding.
     */
    fun pngHeaderClaiming(width: Int, height: Int): ByteArray {
        val ihdr = ByteArrayOutputStream().also { body ->
            DataOutputStream(body).apply {
                writeInt(width)
                writeInt(height)
                writeByte(8) // bit depth
                writeByte(6) // colour type: RGBA
                writeByte(0) // compression
                writeByte(0) // filter
                writeByte(0) // interlace
            }
        }.toByteArray()
        return ByteArrayOutputStream().also { out ->
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            writeChunk(out, "IHDR", ihdr)
        }.toByteArray()
    }

    val svg: ByteArray = """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""".toByteArray()

    private fun image(width: Int, height: Int, noise: Boolean, type: Int): BufferedImage {
        val image = BufferedImage(width, height, type)
        if (noise) {
            val random = Random(42)
            for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, random.nextInt() or (0xFF shl 24))
        } else {
            image.createGraphics().apply {
                color = Color(30, 90, 160)
                fillRect(0, 0, width, height)
                dispose()
            }
        }
        return image
    }

    private fun encode(image: BufferedImage, format: String): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val crc = CRC32().apply {
            update(type.toByteArray())
            update(data)
        }
        DataOutputStream(out).apply {
            writeInt(data.size)
            write(type.toByteArray())
            write(data)
            writeInt(crc.value.toInt())
        }
    }
}
