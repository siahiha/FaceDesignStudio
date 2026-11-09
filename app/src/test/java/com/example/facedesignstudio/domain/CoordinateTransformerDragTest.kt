package com.example.facedesignstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateTransformerDragTest {
    private val tolerance = .0001f

    @Test
    fun zeroMovementKeepsSelectedPointInPlace() {
        val point = FacePoint(.3f, .2f)
        val result = CoordinateTransformer.translateByCanvasDelta(point, FacePoint(0f, 0f), ImageViewport(20f, 90f, 300f, 500f), EditorTransform(scale = 3f, rotationDegrees = 27f))
        assertEquals(point, result)
    }

    @Test
    fun dragMovesPointByTheSameScreenDistanceAtAnyZoomAndRotation() {
        val viewport = ImageViewport(20f, 100f, 320f, 480f)
        val point = FacePoint(.35f, .3f)
        val delta = FacePoint(24f, -18f)
        for (zoom in listOf(1f, 2f, 4f)) for (angle in listOf(-90f, -27f, 0f, 38f, 90f)) {
            val transform = EditorTransform(scale = zoom, rotationDegrees = angle, translationX = .1f, translationY = -.15f)
            val before = CoordinateTransformer.toCanvas(point, viewport, 180f, 340f, transform)
            val moved = CoordinateTransformer.translateByCanvasDelta(point, delta, viewport, transform)
            val after = CoordinateTransformer.toCanvas(moved, viewport, 180f, 340f, transform)
            assertEquals(delta.x, after.x - before.x, tolerance)
            assertEquals(delta.y, after.y - before.y, tolerance)
        }
    }

    @Test
    fun movementDependsOnDisplacementAndNotWhereTheFingerTouches() {
        val viewport = ImageViewport(0f, 0f, 400f, 600f)
        val point = FacePoint(.2f, .25f)
        for (touchStart in listOf(FacePoint(80f, 150f), FacePoint(300f, 500f))) {
            val touchEnd = FacePoint(touchStart.x + 40f, touchStart.y + 30f)
            val moved = CoordinateTransformer.translateByCanvasDelta(point, FacePoint(touchEnd.x - touchStart.x, touchEnd.y - touchStart.y), viewport, EditorTransform())
            assertEquals(.3f, moved.x, tolerance)
            assertEquals(.3f, moved.y, tolerance)
        }
    }

    @Test
    fun incrementalDragsMatchOneContinuousDrag() {
        val viewport = ImageViewport(0f, 30f, 300f, 450f)
        val transform = EditorTransform(scale = 2f, rotationDegrees = 18f)
        val point = FacePoint(.4f, .3f)
        var moved = point
        repeat(20) { moved = CoordinateTransformer.translateByCanvasDelta(moved, FacePoint(1f, -.5f), viewport, transform) }
        val expected = CoordinateTransformer.translateByCanvasDelta(point, FacePoint(20f, -10f), viewport, transform)
        assertEquals(expected.x, moved.x, tolerance)
        assertEquals(expected.y, moved.y, tolerance)
    }

    @Test
    fun letterboxedPhotoUsesItsDisplayedDimensions() {
        val viewport = CoordinateTransformer.fitViewport(400f, 800f, 1600, 800)
        val moved = CoordinateTransformer.translateByCanvasDelta(FacePoint(.5f, .5f), FacePoint(40f, 20f), viewport, EditorTransform())
        assertEquals(.6f, moved.x, tolerance)
        assertEquals(.6f, moved.y, tolerance)
    }

    @Test
    fun imagePanningDoesNotAffectDragSensitivity() {
        val viewport = ImageViewport(20f, 30f, 300f, 450f)
        val point = FacePoint(.4f, .3f)
        val transform = EditorTransform(scale = 2f, rotationDegrees = 18f)
        val delta = FacePoint(15f, -10f)
        val expected = CoordinateTransformer.translateByCanvasDelta(point, delta, viewport, transform)
        val panned = CoordinateTransformer.translateByCanvasDelta(point, delta, viewport, transform.copy(translationX = .7f, translationY = -.5f))
        assertEquals(expected, panned)
    }
}
