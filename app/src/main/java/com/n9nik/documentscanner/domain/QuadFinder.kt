package com.n9nik.documentscanner.domain

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Pure-Kotlin document quadrilateral finder.
 *
 * Operates on a grayscale float array (0..255) with no Android dependencies so it
 * stays unit-testable on the JVM. The Android wrapper ([DocumentDetector]) converts
 * a Bitmap into this form.
 *
 * Pipeline: gaussian blur -> Sobel edge magnitude -> statistical threshold ->
 * dilate -> largest connected component -> convex hull -> simplify to quad.
 *
 * @return 8 floats as tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y in the same
 * coordinate space as the input, or null when no document-like quad is found.
 */
object QuadFinder {

    fun findQuad(gray: FloatArray, width: Int, height: Int): FloatArray? {
        require(gray.size == width * height) { "gray size mismatch" }
        if (width < 40 || height < 40) return null

        val blurred = gaussianBlur(gray, width, height)
        val magnitude = sobelMagnitude(blurred, width, height)

        // Statistical threshold: edges are the bright tail of the distribution.
        var sum = 0.0
        var sumSq = 0.0
        for (v in magnitude) {
            sum += v
            sumSq += v * v
        }
        val n = magnitude.size.toDouble()
        val mean = sum / n
        val variance = (sumSq / n - mean * mean).coerceAtLeast(0.0)
        val std = sqrt(variance)
        if (std < 1e-3) return null // flat image, nothing to find
        val threshold = (mean + std).toFloat()

        val binary = BooleanArray(magnitude.size) { magnitude[it] > threshold }
        dilate3x3(binary, width, height)

        val labels = labelComponents(binary, width, height)
        var bestLabel = -1
        var bestCount = 0
        val counts = HashMap<Int, Int>()
        for (label in labels) {
            if (label == 0) continue
            val c = (counts[label] ?: 0) + 1
            counts[label] = c
            if (c > bestCount) {
                bestCount = c
                bestLabel = label
            }
        }
        // The document border must cover a meaningful fraction of the frame.
        if (bestLabel < 0 || bestCount < width * height * 0.01) return null

        // Collect component pixels (subsampled for speed) and take the convex hull.
        val pts = ArrayList<FloatArray>()
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (labels[i] == bestLabel && (x + y) % 3 == 0) {
                    pts.add(floatArrayOf(x.toFloat(), y.toFloat()))
                }
                i++
            }
        }
        if (pts.size < 8) return null
        val hull = convexHull(pts)
        if (hull.size < 4) return null

        val quad = simplifyToQuad(hull) ?: extremeQuad(hull)
        return orderQuad(quad)
    }

    private fun gaussianBlur(src: FloatArray, w: Int, h: Int): FloatArray {
        val k = floatArrayOf(1f, 4f, 6f, 4f, 1f)
        val tmp = FloatArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var acc = 0f
                for (d in -2..2) {
                    val xx = (x + d).coerceIn(0, w - 1)
                    acc += src[row + xx] * k[d + 2]
                }
                tmp[row + x] = acc / 16f
            }
        }
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var acc = 0f
                for (d in -2..2) {
                    val yy = (y + d).coerceIn(0, h - 1)
                    acc += tmp[yy * w + x] * k[d + 2]
                }
                out[y * w + x] = acc / 16f
            }
        }
        return out
    }

    private fun sobelMagnitude(src: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = -src[i - w - 1] - 2f * src[i - 1] - src[i + w - 1] +
                        src[i - w + 1] + 2f * src[i + 1] + src[i + w + 1]
                val gy = -src[i - w - 1] - 2f * src[i - w] - src[i - w + 1] +
                        src[i + w - 1] + 2f * src[i + w] + src[i + w + 1]
                out[i] = hypot(gx.toDouble(), gy.toDouble()).toFloat()
            }
        }
        return out
    }

    private fun dilate3x3(binary: BooleanArray, w: Int, h: Int) {
        val src = binary.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (src[y * w + x]) continue
                var any = false
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val xx = x + dx
                        val yy = y + dy
                        if (xx in 0 until w && yy in 0 until h && src[yy * w + xx]) {
                            any = true
                            break
                        }
                    }
                    if (any) break
                }
                binary[y * w + x] = any
            }
        }
    }

    /** Single-pass BFS connected-component labeling. Returns label per pixel (0 = background). */
    private fun labelComponents(binary: BooleanArray, w: Int, h: Int): IntArray {
        val labels = IntArray(w * h)
        val stack = IntArray(w * h)
        var nextLabel = 0
        for (s in binary.indices) {
            if (!binary[s] || labels[s] != 0) continue
            nextLabel++
            var top = 0
            stack[top++] = s
            labels[s] = nextLabel
            while (top > 0) {
                val cur = stack[--top]
                val cx = cur % w
                val cy = cur / w
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = cx + dx
                        val ny = cy + dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val ni = ny * w + nx
                        if (binary[ni] && labels[ni] == 0) {
                            labels[ni] = nextLabel
                            stack[top++] = ni
                        }
                    }
                }
            }
        }
        return labels
    }

    /** Andrew's monotone chain convex hull. */
    internal fun convexHull(points: List<FloatArray>): List<FloatArray> {
        val sorted = points.sortedWith(compareBy({ it[0] }, { it[1] }))
        if (sorted.size <= 1) return sorted
        fun cross(o: FloatArray, a: FloatArray, b: FloatArray): Float =
            (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

        val lower = ArrayList<FloatArray>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }
        val upper = ArrayList<FloatArray>()
        for (i in sorted.size - 1 downTo 0) {
            val p = sorted[i]
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }

    /** Douglas-Peucker on a closed polygon; returns the 4 corners or null. */
    private fun simplifyToQuad(hull: List<FloatArray>): List<FloatArray>? {
        if (hull.size == 4) return hull
        var perimeter = 0.0
        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            perimeter += hypot((b[0] - a[0]).toDouble(), (b[1] - a[1]).toDouble())
        }
        val simplified = douglasPeuckerClosed(hull, perimeter * 0.02)
        return if (simplified.size == 4) simplified else null
    }

    private fun douglasPeuckerClosed(poly: List<FloatArray>, epsilon: Double): List<FloatArray> {
        // Open the polygon at the point farthest from the first edge for stability.
        val open = poly + poly[0]
        val keep = BooleanArray(open.size)
        keep[0] = true
        keep[open.size - 1] = true
        simplifyRecursive(open, 0, open.size - 1, epsilon, keep)
        return open.filterIndexed { idx, _ -> keep[idx] }.dropLast(1)
    }

    private fun simplifyRecursive(
        pts: List<FloatArray>,
        first: Int,
        last: Int,
        epsilon: Double,
        keep: BooleanArray
    ) {
        if (last - first < 2) return
        val a = pts[first]
        val b = pts[last]
        var maxDist = 0.0
        var maxIdx = -1
        for (i in first + 1 until last) {
            val d = pointLineDistance(pts[i], a, b)
            if (d > maxDist) {
                maxDist = d
                maxIdx = i
            }
        }
        if (maxDist > epsilon && maxIdx >= 0) {
            keep[maxIdx] = true
            simplifyRecursive(pts, first, maxIdx, epsilon, keep)
            simplifyRecursive(pts, maxIdx, last, epsilon, keep)
        }
    }

    private fun pointLineDistance(p: FloatArray, a: FloatArray, b: FloatArray): Double {
        val dx = (b[0] - a[0]).toDouble()
        val dy = (b[1] - a[1]).toDouble()
        val len = hypot(dx, dy)
        if (len < 1e-9) return hypot((p[0] - a[0]).toDouble(), (p[1] - a[1]).toDouble())
        return abs(dy * p[0] - dx * p[1] + b[0] * a[1] - b[1] * a[0]) / len
    }

    /** Fallback quad from extreme points when simplification doesn't yield 4 corners. */
    private fun extremeQuad(hull: List<FloatArray>): List<FloatArray> {
        var tl = hull[0]; var tr = hull[0]; var br = hull[0]; var bl = hull[0]
        for (p in hull) {
            if (p[0] + p[1] < tl[0] + tl[1]) tl = p
            if (p[0] + p[1] > br[0] + br[1]) br = p
            if (p[0] - p[1] > tr[0] - tr[1]) tr = p
            if (p[0] - p[1] < bl[0] - bl[1]) bl = p
        }
        return listOf(tl, tr, br, bl)
    }

    /** Orders 4 unordered corners as TL, TR, BR, BL. */
    internal fun orderQuad(quad: List<FloatArray>): FloatArray {
        require(quad.size == 4)
        var tl = quad[0]; var tr = quad[0]; var br = quad[0]; var bl = quad[0]
        for (p in quad) {
            if (p[0] + p[1] < tl[0] + tl[1]) tl = p
            if (p[0] + p[1] > br[0] + br[1]) br = p
            if (p[0] - p[1] > tr[0] - tr[1]) tr = p
            if (p[0] - p[1] < bl[0] - bl[1]) bl = p
        }
        return floatArrayOf(tl[0], tl[1], tr[0], tr[1], br[0], br[1], bl[0], bl[1])
    }
}
