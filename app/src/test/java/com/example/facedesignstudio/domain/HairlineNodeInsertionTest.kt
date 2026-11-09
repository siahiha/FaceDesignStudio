package com.example.facedesignstudio.domain

import org.junit.Assert.*
import org.junit.Test

class HairlineNodeInsertionTest {
    private val axis = FaceAxis(FacePoint(.5f, 0f), FacePoint(.5f, 1f))
    private val tolerance = .00001f

    private fun fourNodes(axis: FaceAxis = this.axis, aspect: Float = 1f): List<BezierNode> {
        val outer = BezierNode(.15f, .5f, .15f, .5f, .18f, .33f)
        val inner = BezierNode(.36f, .42f, .25f, .42f, .45f, .48f)
        return listOf(outer, inner, HairlineGeometry.mirrorNode(inner, axis, aspect), HairlineGeometry.mirrorNode(outer, axis, aspect))
    }

    @Test
    fun defaultInsertionAddsDistinctPointsOnBothSides() {
        val original = fourNodes()
        val result = HairlineGeometry.insertNodes(original, null, true)!!
        assertEquals(6, result.nodes.size)
        assertEquals(1, result.selectedIndex)
        assertTrue(result.nodes[1].x < .5f)
        assertTrue(result.nodes[4].x > .5f)
        assertMirrored(result.nodes)
        assertEquals(original[0].x, result.nodes[0].x, tolerance)
        assertEquals(original[1].x, result.nodes[2].x, tolerance)
    }

    @Test
    fun selectingEitherSideInsertsTheSamePairAndKeepsSelectionOnThatSide() {
        for (selected in 0..3) {
            val result = HairlineGeometry.insertNodes(fourNodes(), selected, true)!!
            assertEquals(6, result.nodes.size)
            assertEquals(if (selected < 2) 1 else 4, result.selectedIndex)
            assertEquals(HairlineGeometry.insertNodes(fourNodes(), null, true)!!.nodes, result.nodes)
        }
    }

    @Test
    fun subdivisionPreservesTheEntireOriginalCurve() {
        val original = fourNodes()
        val updated = HairlineGeometry.insertNodes(original, null, true)!!.nodes
        for (segment in 0..2) for (step in 0..40) {
            val t = step / 40f
            val expected = pointOnCurve(original, segment, t)
            val actual = when (segment) {
                0 -> if (t <= .5f) pointOnCurve(updated, 0, t * 2f) else pointOnCurve(updated, 1, (t - .5f) * 2f)
                1 -> pointOnCurve(updated, 2, t)
                else -> if (t <= .5f) pointOnCurve(updated, 3, t * 2f) else pointOnCurve(updated, 4, (t - .5f) * 2f)
            }
            assertEquals(expected.x, actual.x, tolerance)
            assertEquals(expected.y, actual.y, tolerance)
        }
    }

    @Test
    fun repeatedInsertionDoesNotStackNewPointsAtTheCenter() {
        var nodes = fourNodes()
        var selection: Int? = null
        repeat(4) {
            val result = HairlineGeometry.insertNodes(nodes, selection, true)!!
            assertEquals(nodes.size + 2, result.nodes.size)
            nodes = result.nodes
            selection = result.selectedIndex
            assertMirrored(nodes)
            assertTrue(nodes.all { kotlin.math.abs(it.x - .5f) > tolerance })
            for (i in nodes.indices) for (j in i + 1 until nodes.size) {
                assertTrue(kotlin.math.hypot(nodes[i].x - nodes[j].x, nodes[i].y - nodes[j].y) > tolerance)
            }
        }
        assertEquals(12, nodes.size)
    }

    @Test
    fun pairIsSymmetricForATiltedAxisAndPortraitAspectRatio() {
        val tilted = FaceAxis(FacePoint(.45f, .1f), FacePoint(.55f, .9f))
        val result = HairlineGeometry.insertNodes(fourNodes(tilted, .75f), 3, true)!!
        assertMirrored(result.nodes, tilted, .75f)
    }

    @Test
    fun legacyCenterNodeRemainsSingleWhileASidePairIsAdded() {
        val legacy = HairlineGeometry.splitSegment(fourNodes(), 1)
        val result = HairlineGeometry.insertNodes(legacy, 2, true)!!
        assertEquals(7, result.nodes.size)
        assertEquals(1, result.nodes.count { kotlin.math.abs(it.x - .5f) < tolerance })
        assertMirrored(result.nodes)
    }

    @Test
    fun symmetryOffAddsOnlyOnePoint() {
        val result = HairlineGeometry.insertNodes(fourNodes(), 1, false)!!
        assertEquals(5, result.nodes.size)
        assertEquals(2, result.selectedIndex)
        assertEquals(.5f, result.nodes[2].x, tolerance)
    }

    @Test
    fun singleSymmetricSegmentGetsTwoSeparatedPoints() {
        val pair = listOf(BezierNode(.2f,.4f,.2f,.4f,.3f,.5f), BezierNode(.8f,.4f,.7f,.5f,.8f,.4f))
        val result = HairlineGeometry.insertNodes(pair, null, true)!!
        assertEquals(4, result.nodes.size)
        assertTrue(result.nodes[1].x < .5f && result.nodes[2].x > .5f)
        assertMirrored(result.nodes)
    }

    private fun assertMirrored(nodes: List<BezierNode>, axis: FaceAxis = this.axis, aspect: Float = 1f) {
        for (i in 0 until nodes.size / 2) {
            val expected = HairlineGeometry.mirrorNode(nodes[i], axis, aspect)
            val actual = nodes[nodes.lastIndex - i]
            assertEquals(expected.x, actual.x, tolerance)
            assertEquals(expected.y, actual.y, tolerance)
            assertEquals(expected.inX, actual.inX, tolerance)
            assertEquals(expected.inY, actual.inY, tolerance)
            assertEquals(expected.outX, actual.outX, tolerance)
            assertEquals(expected.outY, actual.outY, tolerance)
        }
    }

    private fun pointOnCurve(nodes: List<BezierNode>, segment: Int, t: Float): FacePoint {
        val a = nodes[segment]
        val b = nodes[segment + 1]
        val u = 1f - t
        fun value(p0: Float, p1: Float, p2: Float, p3: Float) = u*u*u*p0 + 3*u*u*t*p1 + 3*u*t*t*p2 + t*t*t*p3
        return FacePoint(value(a.x, a.outX, b.inX, b.x), value(a.y, a.outY, b.inY, b.y))
    }
}
