package com.example.facedesignstudio.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.StaticLayout
import com.example.facedesignstudio.data.local.PatientEntity
import com.example.facedesignstudio.domain.BezierNode
import com.example.facedesignstudio.domain.DetectionAnalysis
import com.example.facedesignstudio.domain.EditorTransform
import com.example.facedesignstudio.domain.CoordinateTransformer
import com.example.facedesignstudio.domain.FacePoint
import com.example.facedesignstudio.domain.HairlineAppearance
import com.example.facedesignstudio.domain.HairlineGeometry
import com.example.facedesignstudio.domain.HairlineProfile
import com.example.facedesignstudio.domain.HairlineReference
import com.example.facedesignstudio.domain.ImageViewport
import java.io.ByteArrayOutputStream
import kotlin.math.min

object PlanPdfExporter {
    fun render(
        photo: Bitmap,
        patient: PatientEntity,
        analysis: DetectionAnalysis,
        nodes: List<BezierNode>,
        transform: EditorTransform,
        version: Int?,
        thresholdPercent: Int?,
        referenceMethod: HairlineReference,
        hairlineProfile: HairlineProfile,
        appearance: HairlineAppearance = HairlineAppearance(),
    ): ByteArray {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(35, 77, 68)
            textSize = 21f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        canvas.drawText("FaceDesign Studio", 36f, 44f, titlePaint)
        drawRtl(canvas, "${patient.firstName} ${patient.lastName}", 36f, 57f, 523f, 28f, 16f, android.graphics.Color.BLACK, true)

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 10.5f }
        canvas.drawText("Patient code: ${patient.patientCode}", 36f, 112f, metaPaint)
        canvas.drawText("Plan version: ${version ?: "Draft"}", 36f, 129f, metaPaint)
        canvas.drawText("Face confidence: ${(analysis.faceConfidence * 100).toInt()}%   ·   Acceptance threshold: ${thresholdPercent ?: "—"}%", 36f, 146f, metaPaint)
        canvas.drawText("Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())}", 36f, 163f, metaPaint)

        val profileLabel = if (hairlineProfile == HairlineProfile.NATURAL) "Natural" else hairlineProfile.name
        canvas.drawText("Reference: ${referenceMethod.name}   ·   Profile: $profileLabel", 36f, 180f, metaPaint)

        val viewport = RectF(25f, 198f, 570f, 810f)
        val scale = min(viewport.width() / photo.width, viewport.height() / photo.height)
        val displayWidth = photo.width * scale
        val displayHeight = photo.height * scale
        val imageRect = RectF(
            viewport.centerX() - displayWidth / 2f,
            viewport.centerY() - displayHeight / 2f,
            viewport.centerX() + displayWidth / 2f,
            viewport.centerY() + displayHeight / 2f,
        )
        val frame = ImageViewport(imageRect.left, imageRect.top, imageRect.width(), imageRect.height())
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.save()
        canvas.clipRect(viewport)
        canvas.translate(viewport.centerX(), viewport.centerY())
        canvas.rotate(transform.rotationDegrees)
        canvas.scale(transform.scale, transform.scale)
        canvas.translate(-viewport.centerX() + transform.translationX * imageRect.width(), -viewport.centerY() + transform.translationY * imageRect.height())
        canvas.drawBitmap(photo, null, imageRect, imagePaint)
        canvas.restore()

        val midlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(99, 180, 255)
            strokeWidth = 1f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 4f), 0f)
        }
        canvas.save()
        canvas.clipRect(viewport)
        HairlineGeometry.facialAxis(analysis.landmarks, photo.width.toFloat() / photo.height.coerceAtLeast(1))?.let { rawAxis ->
            val axis = HairlineGeometry.lineAcrossImage(HairlineGeometry.shiftedAxis(rawAxis, transform.midlineOffset))
            val start = mapPoint(axis.start.x, axis.start.y, frame, viewport.centerX(), viewport.centerY(), transform)
            val end = mapPoint(axis.end.x, axis.end.y, frame, viewport.centerX(), viewport.centerY(), transform)
            canvas.drawLine(start.x, start.y, end.x, end.y, midlinePaint)
        }
        HairlineGeometry.lowerIrisLine(analysis.landmarks)?.let { rawAxis ->
            val axis = HairlineGeometry.lineAcrossImage(rawAxis)
            val start = mapPoint(axis.start.x, axis.start.y, frame, viewport.centerX(), viewport.centerY(), transform)
            val end = mapPoint(axis.end.x, axis.end.y, frame, viewport.centerX(), viewport.centerY(), transform)
            canvas.drawLine(start.x, start.y, end.x, end.y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(220, 99, 180, 255)
                strokeWidth = 1.4f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 4f), 0f)
            })
        }
        val sideGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(120, 140, 200, 255)
            strokeWidth = 0.8f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f)
        }
        analysis.landmarks.filter { it.name == "LEFT_TEMPLE" || it.name == "RIGHT_TEMPLE" }.forEach { temple ->
            val start = mapPoint(temple.x, 0f, frame, viewport.centerX(), viewport.centerY(), transform)
            val end = mapPoint(temple.x, 1f, frame, viewport.centerX(), viewport.centerY(), transform)
            canvas.drawLine(start.x, start.y, end.x, end.y, sideGuidePaint)
        }
        analysis.landmarks.filter { it.name == "LEFT_PUPIL" || it.name == "RIGHT_PUPIL" }.forEach { landmark ->
            val point = mapPoint(landmark.x, landmark.y, frame, viewport.centerX(), viewport.centerY(), transform)
            canvas.drawCircle(point.x, point.y, 1.7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(110, 255, 255, 255) })
        }

        val curve = Path()
        val mapped = nodes.map { node ->
            listOf(
                mapPoint(node.x, node.y, frame, viewport.centerX(), viewport.centerY(), transform),
                mapPoint(node.inX, node.inY, frame, viewport.centerX(), viewport.centerY(), transform),
                mapPoint(node.outX, node.outY, frame, viewport.centerX(), viewport.centerY(), transform),
            )
        }
        if (mapped.isNotEmpty()) {
            curve.moveTo(mapped.first()[0].x, mapped.first()[0].y)
            mapped.zipWithNext().forEach { (from, to) -> curve.cubicTo(from[2].x, from[2].y, to[1].x, to[1].y, to[0].x, to[0].y) }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(85, 0, 0, 0)
                strokeWidth = maxOf(1.5f, appearance.strokeWidthDp.coerceIn(1f, 8f) * displayWidth / 360f) + displayWidth * .004f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(curve, linePaint)
            val blueLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(47, 143, 234)
                strokeWidth = maxOf(1.5f, appearance.strokeWidthDp.coerceIn(1f, 8f) * displayWidth / 360f)
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                if (appearance.dashed) pathEffect = android.graphics.DashPathEffect(floatArrayOf(strokeWidth * 3.5f, strokeWidth * 2.2f), 0f)
            }
            canvas.drawPath(curve, blueLinePaint)
            val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(255, 147, 51); style = Paint.Style.FILL }
            mapped.forEach { canvas.drawCircle(it[0].x, it[0].y, 3.1f, nodePaint) }
        }
        canvas.restore()
        document.finishPage(page)
        return ByteArrayOutputStream().use { output ->
            document.writeTo(output)
            document.close()
            output.toByteArray()
        }
    }

    private fun mapPoint(x: Float, y: Float, rect: ImageViewport, centerX: Float, centerY: Float, transform: EditorTransform): FacePoint =
        CoordinateTransformer.toCanvas(FacePoint(x, y), rect, centerX, centerY, transform)

    private fun drawRtl(canvas: Canvas, text: String, x: Float, y: Float, width: Float, height: Float, size: Float, color: Int, bold: Boolean) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()
            .also { layout ->
                canvas.save()
                canvas.translate(x, y)
                layout.draw(canvas)
                canvas.restore()
            }
    }
}
