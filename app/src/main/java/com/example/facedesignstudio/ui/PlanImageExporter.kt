package com.example.facedesignstudio.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.facedesignstudio.domain.BezierNode
import com.example.facedesignstudio.domain.CoordinateTransformer
import com.example.facedesignstudio.domain.DetectionAnalysis
import com.example.facedesignstudio.domain.EditorTransform
import com.example.facedesignstudio.domain.FacePoint
import com.example.facedesignstudio.domain.HairlineAppearance
import com.example.facedesignstudio.domain.HairlineGeometry
import com.example.facedesignstudio.domain.ImageViewport

object PlanImageExporter {
    fun render(
        photo: Bitmap,
        analysis: DetectionAnalysis,
        nodes: List<BezierNode>,
        transform: EditorTransform,
        appearance: HairlineAppearance = HairlineAppearance(),
        includeReferences: Boolean,
        includeControls: Boolean,
    ): Bitmap {
        val output = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val frame = ImageViewport(0f, 0f, photo.width.toFloat(), photo.height.toFloat())
        val pivotX = photo.width / 2f
        val pivotY = photo.height / 2f
        canvas.save()
        canvas.translate(pivotX, pivotY)
        canvas.rotate(transform.rotationDegrees)
        canvas.scale(transform.scale, transform.scale)
        canvas.translate(-pivotX + transform.translationX * photo.width, -pivotY + transform.translationY * photo.height)
        canvas.drawBitmap(photo, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()

        fun map(x: Float, y: Float) = CoordinateTransformer.toCanvas(FacePoint(x, y), frame, pivotX, pivotY, transform)
        if (includeReferences) {
            val midline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(99, 180, 255)
                strokeWidth = maxOf(1.5f, photo.width * .001f)
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(photo.width * .012f, photo.width * .008f), 0f)
            }
            HairlineGeometry.facialAxis(analysis.landmarks, photo.width.toFloat() / photo.height.coerceAtLeast(1))?.let { rawAxis ->
                val axis = HairlineGeometry.lineAcrossImage(HairlineGeometry.shiftedAxis(rawAxis, transform.midlineOffset))
                val a = map(axis.start.x, axis.start.y)
                val b = map(axis.end.x, axis.end.y)
                canvas.drawLine(a.x, a.y, b.x, b.y, midline)
            }
            HairlineGeometry.lowerIrisLine(analysis.landmarks)?.let { rawAxis ->
                val axis = HairlineGeometry.lineAcrossImage(rawAxis)
                val eyeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(220, 99, 180, 255)
                    strokeWidth = maxOf(1f, photo.width * .00055f)
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(photo.width * .012f, photo.width * .008f), 0f)
                }
                val a = map(axis.start.x, axis.start.y)
                val b = map(axis.end.x, axis.end.y)
                canvas.drawLine(a.x, a.y, b.x, b.y, eyeLinePaint)
            }
            val sideGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(120, 140, 200, 255)
                strokeWidth = maxOf(1f, photo.width * .00035f)
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(photo.width * .01f, photo.width * .01f), 0f)
            }
            analysis.landmarks.filter { it.name == "LEFT_TEMPLE" || it.name == "RIGHT_TEMPLE" }.forEach { temple ->
                val a = map(temple.x, 0f)
                val b = map(temple.x, 1f)
                canvas.drawLine(a.x, a.y, b.x, b.y, sideGuidePaint)
            }
            analysis.landmarks.forEach { landmark ->
                val point = map(landmark.x, landmark.y)
                if (landmark.name == "LEFT_PUPIL" || landmark.name == "RIGHT_PUPIL") {
                    canvas.drawCircle(point.x, point.y, maxOf(1f, photo.width * .0015f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(110, 255, 255, 255) })
                } else if (landmark.name != "LEFT_IRIS_BOTTOM" && landmark.name != "RIGHT_IRIS_BOTTOM") {
                    canvas.drawCircle(point.x, point.y, maxOf(2f, photo.width * .0035f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(255, 209, 102) })
                }
            }
        }
        val mapped = nodes.map { node -> listOf(map(node.x, node.y), map(node.inX, node.inY), map(node.outX, node.outY)) }
        if (mapped.isNotEmpty()) {
            val path = Path().apply {
                moveTo(mapped.first()[0].x, mapped.first()[0].y)
                mapped.zipWithNext().forEach { (from, to) -> cubicTo(from[2].x, from[2].y, to[1].x, to[1].y, to[0].x, to[0].y) }
            }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(85, 0, 0, 0)
                strokeWidth = maxOf(2f, photo.width * (appearance.strokeWidthDp.coerceIn(1f, 8f) / 360f)) + photo.width * .004f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(47, 143, 234)
                strokeWidth = maxOf(2f, photo.width * (appearance.strokeWidthDp.coerceIn(1f, 8f) / 360f))
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                if (appearance.dashed) pathEffect = android.graphics.DashPathEffect(floatArrayOf(strokeWidth * 3.5f, strokeWidth * 2.2f), 0f)
            })
            if (includeControls) {
                val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(255, 147, 51) }
                val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
                mapped.forEach { points ->
                    canvas.drawLine(points[1].x, points[1].y, points[0].x, points[0].y, handlePaint)
                    canvas.drawLine(points[0].x, points[0].y, points[2].x, points[2].y, handlePaint)
                    canvas.drawCircle(points[1].x, points[1].y, maxOf(2f, photo.width * .003f), handlePaint)
                    canvas.drawCircle(points[2].x, points[2].y, maxOf(2f, photo.width * .003f), handlePaint)
                    canvas.drawCircle(points[0].x, points[0].y, maxOf(3f, photo.width * .005f), nodePaint)
                }
            }
        }
        return output
    }
}
