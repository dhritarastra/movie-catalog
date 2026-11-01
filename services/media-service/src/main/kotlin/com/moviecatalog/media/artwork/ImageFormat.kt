package com.moviecatalog.media.artwork

enum class ImageFormat(val contentType: String, val imageIoName: String, val extension: String) {
    JPEG("image/jpeg", "jpeg", "jpg"),
    PNG("image/png", "png", "png"),
    ;

    companion object {
        fun ofContentType(contentType: String): ImageFormat = entries.single { it.contentType == contentType }
    }
}
