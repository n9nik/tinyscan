package com.n9nik.documentscanner.domain

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max

/**
 * Document processing: perspective crop, enhance filters, multi-page PDF
 * creation, saving to Downloads, and sharing. All offline.
 */
object DocumentProcessor {

    enum class EnhanceMode { COLOR, GRAYSCALE, BW }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float =
        hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()

    /**
     * Perspective-crops [src] to the quadrilateral [quad]
     * (tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y).
     */
    fun perspectiveCrop(src: Bitmap, quad: FloatArray): Bitmap {
        require(quad.size == 8)
        val tlx = quad[0]; val tly = quad[1]
        val trx = quad[2]; val try_ = quad[3]
        val brx = quad[4]; val bry = quad[5]
        val blx = quad[6]; val bly = quad[7]

        val widthTop = dist(tlx, tly, trx, try_)
        val widthBottom = dist(blx, bly, brx, bry)
        val heightLeft = dist(tlx, tly, blx, bly)
        val heightRight = dist(trx, try_, brx, bry)
        val dw = max(widthTop, widthBottom).toInt().coerceAtLeast(1)
        val dh = max(heightLeft, heightRight).toInt().coerceAtLeast(1)

        val matrix = Matrix()
        matrix.setPolyToPoly(
            floatArrayOf(tlx, tly, trx, try_, brx, bry, blx, bly), 0,
            floatArrayOf(0f, 0f, dw.toFloat(), 0f, dw.toFloat(), dh.toFloat(), 0f, dh.toFloat()), 0,
            4
        )
        val out = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Applies the enhance filter; returns a new bitmap (COLOR boosts slightly). */
    fun enhance(src: Bitmap, mode: EnhanceMode): Bitmap {
        val cm = ColorMatrix()
        when (mode) {
            EnhanceMode.GRAYSCALE -> cm.setSaturation(0f)
            EnhanceMode.BW -> {
                cm.setSaturation(0f)
                val contrast = ColorMatrix(
                    floatArrayOf(
                        2.6f, 0f, 0f, 0f, -165f,
                        0f, 2.6f, 0f, 0f, -165f,
                        0f, 0f, 2.6f, 0f, -165f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(contrast)
            }
            EnhanceMode.COLOR -> {
                // Gentle punch: a touch more saturation and contrast.
                cm.setSaturation(1.12f)
                val contrast = ColorMatrix(
                    floatArrayOf(
                        1.06f, 0f, 0f, 0f, -8f,
                        0f, 1.06f, 0f, 0f, -8f,
                        0f, 0f, 1.06f, 0f, -8f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(contrast)
            }
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /**
     * Renders [pages] into a multi-page A4 PDF in the app cache dir.
     * No watermark, no cloud — the key differentiator.
     */
    fun createPdf(context: Context, pages: List<Bitmap>): File {
        require(pages.isNotEmpty())
        val doc = PdfDocument()
        // A4 at 72 dpi
        val pageW = 595
        val pageH = 842
        try {
            pages.forEachIndexed { index, bmp ->
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                val page = doc.startPage(info)
                page.canvas.drawColor(Color.WHITE)
                val scale = minOf(pageW / bmp.width.toFloat(), pageH / bmp.height.toFloat())
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val dx = (pageW - dw) / 2f
                val dy = (pageH - dh) / 2f
                page.canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), Paint(Paint.FILTER_BITMAP_FLAG))
                doc.finishPage(page)
            }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val out = File(context.cacheDir, "tinyscan-$stamp.pdf")
            FileOutputStream(out).use { doc.writeTo(it) }
            return out
        } finally {
            doc.close()
        }
    }

    /** Copies the PDF into Downloads/TinyScan via MediaStore. Returns the content Uri. */
    fun savePdfToDownloads(context: Context, pdf: File): Uri? {
        val fileName = pdf.name
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TinyScan")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                pdf.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
        return uri
    }

    fun sharePdf(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share PDF"))
    }
}
