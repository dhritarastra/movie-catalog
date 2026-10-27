package com.moviecatalog.media.artwork

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Stores an [ImageFormat] as its content type, which is what the `content_type` CHECK allows. */
@Converter
class ImageFormatConverter : AttributeConverter<ImageFormat, String> {
    override fun convertToDatabaseColumn(format: ImageFormat?): String? = format?.contentType

    override fun convertToEntityAttribute(contentType: String?): ImageFormat? = contentType?.let(ImageFormat::ofContentType)
}
