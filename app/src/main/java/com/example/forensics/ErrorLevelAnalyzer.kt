package com.example.forensics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ElaAnalysisResult(
    val heatmapBitmap: Bitmap,
    val anomalyScore: Float, // 0.0 to 100.0%
    val isTampered: Boolean,
    val suspiciousClusterCount: Int,
    val summary: String
)

object ErrorLevelAnalyzer {

    suspend fun analyze(sourceBitmap: Bitmap): ElaAnalysisResult = withContext(Dispatchers.Default) {
        // 1. Normalize size for fast mobile processing (max 800px on longest edge)
        val maxDim = 800
        val scale = if (sourceBitmap.width > maxDim || sourceBitmap.height > maxDim) {
            maxDim.toFloat() / max(sourceBitmap.width, sourceBitmap.height)
        } else 1.0f

        val workingWidth = (sourceBitmap.width * scale).toInt().coerceAtLeast(100)
        val workingHeight = (sourceBitmap.height * scale).toInt().coerceAtLeast(100)

        val scaledOriginal = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(sourceBitmap, workingWidth, workingHeight, true)
        } else {
            sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        // 2. Compress to JPEG at 90% quality
        val baos = ByteArrayOutputStream()
        scaledOriginal.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val compressedBytes = baos.toByteArray()
        val reloadedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

        // 3. Pixel-by-pixel difference analysis
        val width = workingWidth
        val height = workingHeight
        val heatmapBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val origPixels = IntArray(width * height)
        val reloadedPixels = IntArray(width * height)
        val heatmapPixels = IntArray(width * height)

        scaledOriginal.getPixels(origPixels, 0, width, 0, 0, width, height)
        reloadedBitmap.getPixels(reloadedPixels, 0, width, 0, 0, width, height)

        var totalDifference = 0L
        var severeDiffCount = 0
        var extremeDiffCount = 0
        val scaleFactor = 12

        // Compute error levels
        for (i in 0 until (width * height)) {
            val pOrig = origPixels[i]
            val pRel = reloadedPixels[i]

            val rDiff = abs(Color.red(pOrig) - Color.red(pRel)) * scaleFactor
            val gDiff = abs(Color.green(pOrig) - Color.green(pRel)) * scaleFactor
            val bDiff = abs(Color.blue(pOrig) - Color.blue(pRel)) * scaleFactor

            val maxDiff = max(rDiff, max(gDiff, bDiff))
            totalDifference += maxDiff

            // Forensic Heatmap coloring:
            // Standard text edges naturally reach 50-120 with scaleFactor 12.
            // Severe compression variance (> 200) indicates re-saved edited overlay layers.
            val heatmapColor = when {
                maxDiff > 210 -> {
                    extremeDiffCount++
                    Color.argb(255, 255, 30, 60) // Neon Red (Extreme overlay anomaly)
                }
                maxDiff > 140 -> {
                    severeDiffCount++
                    Color.argb(255, 255, 170, 0) // Amber (High compression edge)
                }
                maxDiff > 60 -> {
                    Color.argb(255, 0, 180, 216) // Cyan (Normal text/vector edge)
                }
                maxDiff > 25 -> {
                    Color.argb(255, 10, 35, 70) // Dark Navy
                }
                else -> {
                    Color.argb(255, 5, 10, 20) // Deep Black (Uniform background)
                }
            }
            heatmapPixels[i] = heatmapColor
        }

        heatmapBitmap.setPixels(heatmapPixels, 0, width, 0, 0, width, height)

        // Clean up intermediate bitmaps if allocated
        if (scaledOriginal != sourceBitmap) {
            scaledOriginal.recycle()
        }
        reloadedBitmap.recycle()

        val totalPixels = (width * height).toFloat()
        val extremeRatio = extremeDiffCount / totalPixels
        val severeRatio = severeDiffCount / totalPixels

        // In a normal screenshot with sharp black/white text, extremeRatio is typically < 0.015 (1.5%)
        // Digitally edited slips with mismatched JPEG artifacts have extremeRatio > 0.05 (5%) or high isolated clustering
        val rawScore = ((extremeRatio * 600f) + (severeRatio * 100f)) * 10f
        val anomalyScore = min(100f, max(0f, rawScore))
        val suspiciousClusters = (extremeDiffCount / 1200).coerceAtLeast(0)

        // Genuine screenshots must NOT be flagged as tampered.
        // Tampering requires genuine high statistical anomaly (> 65% score AND multiple large extreme clusters)
        val isTampered = anomalyScore >= 68f && suspiciousClusters >= 10

        val summary = if (isTampered) {
            "Severe compression layer discrepancy detected (Anomaly: ${String.format("%.1f", anomalyScore)}%). Likely digital edit or fake app generator overlay."
        } else {
            "Pixel compression layers are uniform and consistent with standard mobile screenshots."
        }

        ElaAnalysisResult(
            heatmapBitmap = heatmapBitmap,
            anomalyScore = String.format("%.1f", anomalyScore).toFloat(),
            isTampered = isTampered,
            suspiciousClusterCount = suspiciousClusters,
            summary = summary
        )
    }
}
