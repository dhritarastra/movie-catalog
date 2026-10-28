package com.moviecatalog.media.artwork

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ImageFormatConverterTest {
    private val converter = ImageFormatConverter()

    @Test
    fun `stores each format as its content type and reads it back`() {
        ImageFormat.entries.forEach {
            val column = converter.convertToDatabaseColumn(it)
            assertThat(converter.convertToEntityAttribute(column)).isEqualTo(it)
        }
        assertThat(converter.convertToDatabaseColumn(ImageFormat.JPEG)).isEqualTo("image/jpeg")
        assertThat(converter.convertToDatabaseColumn(ImageFormat.PNG)).isEqualTo("image/png")
    }

    @Test
    fun `passes null through both ways`() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
        assertThat(converter.convertToEntityAttribute(null)).isNull()
    }

    @Test
    fun `an unknown content type is an error, never a guess`() {
        assertThrows<NoSuchElementException> { converter.convertToEntityAttribute("image/gif") }
    }
}
