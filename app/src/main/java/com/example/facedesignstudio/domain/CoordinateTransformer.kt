package com.example.facedesignstudio.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class ImageViewport(val left: Float, val top: Float, val width: Float, val height: Float)

/** Owns the image-space ↔ view-space mapping used by the editor and exported preview. */
object CoordinateTransformer {
    /** Moves an image-space point by a screen-space drag without snapping it to the finger. */
    fun translateByCanvasDelta(
        point: FacePoint,
        delta: FacePoint,
        viewport: ImageViewport,
        transform: EditorTransform,
    ): FacePoint {
        if (viewport.width <= 0f || viewport.height <= 0f || transform.scale <= 0f) return point
        val radians = -transform.rotationDegrees * PI.toFloat() / 180f
        val dx = (delta.x * cos(radians) - delta.y * sin(radians)) / transform.scale
        val dy = (delta.x * sin(radians) + delta.y * cos(radians)) / transform.scale
        return FacePoint(point.x + dx / viewport.width, point.y + dy / viewport.height)
    }

    fun fitViewport(canvasWidth: Float, canvasHeight: Float, imageWidth: Int, imageHeight: Int): ImageViewport {
        if (imageWidth <= 0 || imageHeight <= 0) return ImageViewport(0f, 0f, canvasWidth, canvasHeight)
        val ratio = min(canvasWidth / imageWidth, canvasHeight / imageHeight)
        val width = imageWidth * ratio
        val height = imageHeight * ratio
        return ImageViewport((canvasWidth - width) / 2f, (canvasHeight - height) / 2f, width, height)
    }

    fun toCanvas(
        point: FacePoint,
        viewport: ImageViewport,
        pivotX: Float,
        pivotY: Float,
        transform: EditorTransform,
    ): FacePoint {
        var x = viewport.left + point.x * viewport.width + transform.translationX * viewport.width
        var y = viewport.top + point.y * viewport.height + transform.translationY * viewport.height
        x = pivotX + (x - pivotX) * transform.scale
        y = pivotY + (y - pivotY) * transform.scale
        val radians = transform.rotationDegrees * PI.toFloat() / 180f
        val dx = x - pivotX
        val dy = y - pivotY
        return FacePoint(
            x = pivotX + dx * cos(radians) - dy * sin(radians),
            y = pivotY + dx * sin(radians) + dy * cos(radians),
        )
    }

    fun toImage(
        point: FacePoint,
        viewport: ImageViewport,
        pivotX: Float,
        pivotY: Float,
        transform: EditorTransform,
    ): FacePoint {
        val radians = -transform.rotationDegrees * PI.toFloat() / 180f
        val dx = point.x - pivotX
        val dy = point.y - pivotY
        val unrotatedX = pivotX + dx * cos(radians) - dy * sin(radians)
        val unrotatedY = pivotY + dx * sin(radians) + dy * cos(radians)
        val x = pivotX + (unrotatedX - pivotX) / transform.scale - transform.translationX * viewport.width
        val y = pivotY + (unrotatedY - pivotY) / transform.scale - transform.translationY * viewport.height
        return FacePoint((x - viewport.left) / viewport.width, (y - viewport.top) / viewport.height)
    }
}
