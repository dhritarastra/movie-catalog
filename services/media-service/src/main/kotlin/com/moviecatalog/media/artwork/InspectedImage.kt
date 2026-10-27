package com.moviecatalog.media.artwork

import java.awt.image.BufferedImage

/** An upload that passed ImageInspector: its format, from the file signature, and its decoded pixels. */
class InspectedImage(val format: ImageFormat, val image: BufferedImage)
