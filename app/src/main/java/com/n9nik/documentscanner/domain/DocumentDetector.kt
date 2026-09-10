package com.n9nik.documentscanner.domain

import android.graphics.Bitmap

/**
 * Android wrapper around [QuadFinder]: converts a Bitmap to grayscale and maps
 * the detected quad back to full-resolution coordinates.
 *
 * Returns null when no document-like quad is found; callers fall back to the
 * full-image rectangle so manual corner adjustment always works.
 */
object DocumentDetector {

    /** Detected quad as tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y, or null. */
    fun detectQuad(bitmap: Bitmap): FloatArray? {
        val maxDim = 600
        val scale = minOf(1f, maxDim / maxOf(bitmap.width, bitmap.height).toFloat())
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        try {
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            val gray = FloatArray(w * h)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xff
                val g = (p shr 8) and 0xff
                val b = p and 0xff
                gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
            }
            val quad = QuadFinder.findQuad(gray, w, h) ?: return null
            return FloatArray(8) { i -> quad[i] / scale }
        } finally {
            small.recycle()
        }
    }

    /** Full-image rectangle as a quad, used when auto-detection finds nothing. */
    fun fullImageQuad(width: Int, height: Int): FloatArray =
        floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat()
        )
}
