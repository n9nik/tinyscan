package com.n9nik.documentscanner.domain

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * JVM tests for [QuadFinder] using synthetic grayscale images.
 * No Android dependencies — the algorithm operates on raw float arrays.
 */
class QuadFinderTest {

    /** Fills a rotated rectangle (convex quad) with [value] on a [bg] background. */
    private fun syntheticQuadImage(
        w: Int,
        h: Int,
        corners: List<Pair<Float, Float>>,
        value: Float = 255f,
        bg: Float = 20f
    ): FloatArray {
        val img = FloatArray(w * h) { bg }
        // Scanline fill of the convex quad.
        for (y in 0 until h) {
            val xs = mutableListOf<Float>()
            for (i in corners.indices) {
                val (x1, y1) = corners[i]
                val (x2, y2) = corners[(i + 1) % corners.size]
                if ((y1 <= y && y2 > y) || (y2 <= y && y1 > y)) {
                    val t = (y - y1) / (y2 - y1)
                    xs.add(x1 + t * (x2 - x1))
                }
            }
            xs.sort()
            for (k in xs.indices step 2) {
                if (k + 1 >= xs.size) break
                val xStart = xs[k].toInt().coerceIn(0, w - 1)
                val xEnd = xs[k + 1].toInt().coerceIn(0, w - 1)
                for (x in xStart..xEnd) img[y * w + x] = value
            }
        }
        return img
    }

    private fun nearestDistance(
        quad: FloatArray,
        expected: List<Pair<Float, Float>>
    ): Double {
        // Match each expected corner to its nearest detected corner.
        var total = 0.0
        for ((ex, ey) in expected) {
            var best = Double.MAX_VALUE
            for (i in 0 until 4) {
                val dx = quad[i * 2] - ex
                val dy = quad[i * 2 + 1] - ey
                best = minOf(best, hypot(dx.toDouble(), dy.toDouble()))
            }
            total += best
        }
        return total / expected.size
    }

    @Test
    fun findQuad_detectsRotatedRectangle() {
        val w = 240
        val h = 240
        // A rotated "document" on a dark background, given as TL, TR, BR, BL.
        val expected = listOf(
            60f to 40f,
            190f to 60f,
            170f to 200f,
            40f to 180f
        )
        val img = syntheticQuadImage(w, h, expected)

        val quad = QuadFinder.findQuad(img, w, h)

        assertNotNull("expected a quad for a clear rotated rectangle", quad)
        val avgErr = nearestDistance(quad!!, expected)
        assertTrue("corners too far off: avg error $avgErr px", avgErr < 14.0)
    }

    @Test
    fun findQuad_detectsAxisAlignedRectangle() {
        val w = 200
        val h = 200
        val expected = listOf(
            30f to 50f,
            170f to 50f,
            170f to 160f,
            30f to 160f
        )
        val img = syntheticQuadImage(w, h, expected)

        val quad = QuadFinder.findQuad(img, w, h)

        assertNotNull(quad)
        val avgErr = nearestDistance(quad!!, expected)
        assertTrue("corners too far off: avg error $avgErr px", avgErr < 10.0)
    }

    @Test
    fun findQuad_returnsNullForFlatImage() {
        val w = 120
        val h = 120
        val img = FloatArray(w * h) { 128f }
        assertNull(QuadFinder.findQuad(img, w, h))
    }

    @Test
    fun findQuad_returnsNullForTinySpeck() {
        val w = 200
        val h = 200
        val img = FloatArray(w * h) { 20f }
        // A tiny bright speck (< 1% of area) is not a document.
        for (y in 90 until 100) {
            for (x in 90 until 100) img[y * w + x] = 255f
        }
        assertNull(QuadFinder.findQuad(img, w, h))
    }

    @Test
    fun orderQuad_ordersCornersCorrectly() {
        // Shuffled corners of a simple rect.
        val shuffled = listOf(
            floatArrayOf(170f, 160f), // br
            floatArrayOf(30f, 50f), // tl
            floatArrayOf(30f, 160f), // bl
            floatArrayOf(170f, 50f) // tr
        )
        val ordered = QuadFinder.orderQuad(shuffled)
        // tl, tr, br, bl
        assertTrue(hypot((ordered[0] - 30f).toDouble(), (ordered[1] - 50f).toDouble()) < 1e-3)
        assertTrue(hypot((ordered[2] - 170f).toDouble(), (ordered[3] - 50f).toDouble()) < 1e-3)
        assertTrue(hypot((ordered[4] - 170f).toDouble(), (ordered[5] - 160f).toDouble()) < 1e-3)
        assertTrue(hypot((ordered[6] - 30f).toDouble(), (ordered[7] - 160f).toDouble()) < 1e-3)
    }
}
