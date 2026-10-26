package com.nobodybuilds.listens.data.party

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Local QR encoding for the party join link (no network involved). */
object PartyQr {

    /** Module matrix (1 module per cell, with a quiet-zone [margin]). */
    fun matrix(text: String, margin: Int = 2): BitMatrix {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to margin,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        // Width/height 0 → zxing returns the minimal matrix (one pixel per module).
        return QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    }

    /** Self-contained SVG, always dark-on-white so every scanner reads it (also in dark mode). */
    fun svg(text: String, margin: Int = 2): String {
        val m = matrix(text, margin)
        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
            .append(m.width).append(' ').append(m.height)
            .append("\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#111113\" d=\"")
        for (y in 0 until m.height) {
            var x = 0
            while (x < m.width) {
                if (m.get(x, y)) {
                    val start = x
                    while (x < m.width && m.get(x, y)) x++
                    sb.append('M').append(start).append(' ').append(y)
                        .append('h').append(x - start).append("v1h-").append(x - start).append('z')
                } else {
                    x++
                }
            }
        }
        sb.append("\"/></svg>")
        return sb.toString()
    }

    /** ARGB pixels at [scale] px per module — for an Android Bitmap. */
    fun pixels(text: String, scale: Int, dark: Int, light: Int, margin: Int = 2): Triple<Int, Int, IntArray> {
        val m = matrix(text, margin)
        val w = m.width * scale
        val h = m.height * scale
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val my = y / scale
            val row = y * w
            for (x in 0 until w) {
                px[row + x] = if (m.get(x / scale, my)) dark else light
            }
        }
        return Triple(w, h, px)
    }
}
