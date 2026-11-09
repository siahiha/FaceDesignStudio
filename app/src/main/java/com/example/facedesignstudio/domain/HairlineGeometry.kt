package com.example.facedesignstudio.domain

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

enum class BezierHandleMode { MIRRORED, INDEPENDENT }

data class BezierNode(
    val x: Float,
    val y: Float,
    val inX: Float,
    val inY: Float,
    val outX: Float,
    val outY: Float,
    val handleMode: BezierHandleMode = BezierHandleMode.INDEPENDENT,
)

data class FaceAxis(val start: FacePoint, val end: FacePoint)

data class HairlineAppearance(
    val strokeWidthDp: Float = 3f,
    val dashed: Boolean = false,
    val naturalness: Float = 0.45f,
)

data class EditorTransform(
    val rotationDegrees: Float = 0f,
    val scale: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val midlineOffset: Float = 0f,
)

data class HairlineProposal(
    val nodes: List<BezierNode>,
    val transform: EditorTransform,
)

data class NodeInsertion(val nodes: List<BezierNode>, val selectedIndex: Int)

enum class HairlineReference { FACIAL_PROPORTION, MANUAL }
/** Presets used to generate the first editable hairline proposal. MODERATE is kept for old plans. */
enum class HairlineProfile { CURRENT, CONSERVATIVE, LOWER, MODERATE, NATURAL, CUSTOM }

object HairlineGeometry {
    const val ALGORITHM_VERSION = "1.7.0"
    private const val TEMPORAL_RISE_EYE_RATIO = 0.12f
    private const val CENTER_RISE_EYE_RATIO = 1.05f

    fun initialProposal(
        analysis: DetectionAnalysis,
        reference: HairlineReference = HairlineReference.FACIAL_PROPORTION,
        profile: HairlineProfile = HairlineProfile.CURRENT,
        customRiseRatio: Float = CENTER_RISE_EYE_RATIO,
        naturalness: Float = 0.45f,
        imageAspectRatio: Float = 1f,
    ): HairlineProposal {
        val landmarks = analysis.landmarks
        val leftTemple = landmarks.byName("LEFT_TEMPLE")
        val rightTemple = landmarks.byName("RIGHT_TEMPLE")
        val leftEye = landmarks.firstByName("LEFT_PUPIL") ?: landmarks.byName("LEFT_EYE_LOWER")
        val rightEye = landmarks.firstByName("RIGHT_PUPIL") ?: landmarks.byName("RIGHT_EYE_LOWER")
        val eyeDistance = pixelDistance(
            FacePoint(leftEye.x, leftEye.y),
            FacePoint(rightEye.x, rightEye.y),
            imageAspectRatio,
        ).coerceAtLeast(0.01f)
        val eyeMidY = (leftEye.y + rightEye.y) / 2f
        val riseRatio = when (profile) {
            HairlineProfile.CONSERVATIVE -> 1.12f
            HairlineProfile.LOWER -> 0.92f
            HairlineProfile.CURRENT, HairlineProfile.MODERATE, HairlineProfile.NATURAL -> CENTER_RISE_EYE_RATIO
            HairlineProfile.CUSTOM -> customRiseRatio.coerceIn(0.75f, 1.45f)
        }
        val centerY = eyeMidY - eyeDistance * riseRatio
        val temporalY = centerY - eyeDistance * TEMPORAL_RISE_EYE_RATIO
        val transitionLift = when (profile) {
            HairlineProfile.CONSERVATIVE -> 0.030f
            HairlineProfile.LOWER -> 0.000f
            else -> 0.015f
        }
        val transitionY = centerY - eyeDistance * transitionLift
        val endpointHook = when (profile) {
            HairlineProfile.CONSERVATIVE -> 0.70f
            HairlineProfile.LOWER -> 1.00f
            else -> 0.85f
        }
        val axis = facialAxis(landmarks, imageAspectRatio)
        val midlineX = axis?.let { xAtY(it, centerY) } ?: analysis.midlineX
        val spanLeft = (midlineX - leftTemple.x).coerceAtLeast(0.025f)
        val spanRight = (rightTemple.x - midlineX).coerceAtLeast(0.025f)

        val anchors = if (reference == HairlineReference.MANUAL) {
            listOf(
                FacePoint(leftTemple.x, centerY),
                FacePoint(leftTemple.x + spanLeft * 0.55f, centerY),
                FacePoint(rightTemple.x - spanRight * 0.55f, centerY),
                FacePoint(rightTemple.x, centerY),
            )
        } else {
            listOf(
                FacePoint(leftTemple.x, temporalY),
                FacePoint(leftTemple.x + spanLeft * 0.55f, transitionY),
                FacePoint(rightTemple.x - spanRight * 0.55f, transitionY),
                FacePoint(rightTemple.x, temporalY),
            ).map { FacePoint(stableCoordinate(it.x), stableCoordinate(it.y)) }
        }

        val nodes = nodesFromAnchors(
            anchors = anchors,
            imageAspectRatio = imageAspectRatio,
            endpointHookStrength = if (reference == HairlineReference.MANUAL) 0f else endpointHook,
        )
        return HairlineProposal(
            nodes = nodes,
            transform = EditorTransform(rotationDegrees = alignmentRotationDegrees(landmarks, imageAspectRatio)),
        )
    }

    fun facialAxis(landmarks: List<FacialLandmark>, imageAspectRatio: Float = 1f): FaceAxis? {
        val lowerLine = lowerIrisLine(landmarks) ?: return null
        val leftCorneaCenter = landmarks.firstByName("LEFT_PUPIL") ?: return null
        val rightCorneaCenter = landmarks.firstByName("RIGHT_PUPIL") ?: return null

        val aspect = imageAspectRatio.coerceAtLeast(0.1f)
        val lowerDx = (lowerLine.end.x - lowerLine.start.x) * aspect
        val lowerDy = lowerLine.end.y - lowerLine.start.y
        val lowerLength = hypot(lowerDx, lowerDy)
        if (!lowerLength.isFinite() || lowerLength < 0.000001f) return null

        // The axis passes through the midpoint of the two cornea-center points and
        // uses the normalized perpendicular to the lower-cornea line as its direction.
        val midpoint = FacePoint(
            (leftCorneaCenter.x + rightCorneaCenter.x) / 2f,
            (leftCorneaCenter.y + rightCorneaCenter.y) / 2f,
        )
        val normalX = -lowerDy / lowerLength
        val normalY = lowerDx / lowerLength
        val halfLength = 0.5f
        return FaceAxis(
            FacePoint(midpoint.x - normalX * halfLength / aspect, midpoint.y - normalY * halfLength),
            FacePoint(midpoint.x + normalX * halfLength / aspect, midpoint.y + normalY * halfLength),
        )
    }

    fun lowerIrisLine(landmarks: List<FacialLandmark>): FaceAxis? {
        val left = landmarks.firstByName("LEFT_IRIS_BOTTOM") ?: landmarks.firstByName("LEFT_EYE_LOWER")
        val right = landmarks.firstByName("RIGHT_IRIS_BOTTOM") ?: landmarks.firstByName("RIGHT_EYE_LOWER")
        return if (left == null || right == null) null else FaceAxis(FacePoint(left.x, left.y), FacePoint(right.x, right.y))
    }

    fun alignmentRotationDegrees(landmarks: List<FacialLandmark>, imageAspectRatio: Float): Float {
        val symmetryAxis = facialAxis(landmarks, imageAspectRatio) ?: return 0f
        val axisAngle = lineAngleDegrees(symmetryAxis, imageAspectRatio)
        // A line has no direction: normalize modulo 180° so endpoint order cannot flip the correction.
        return normalizeLineRotation(90f - axisAngle)
    }

    fun lineAcrossImage(axis: FaceAxis): FaceAxis {
        val dx = axis.end.x - axis.start.x
        val dy = axis.end.y - axis.start.y
        val points = mutableListOf<FacePoint>()
        fun addIfInside(x: Float, y: Float) {
            if (x.isFinite() && y.isFinite() && x in -0.0001f..1.0001f && y in -0.0001f..1.0001f) {
                val point = FacePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
                if (points.none { pixelDistance(it, point, 1f) < 0.0001f }) points.add(point)
            }
        }
        if (abs(dx) > 0.000001f) {
            val leftT = -axis.start.x / dx
            addIfInside(0f, axis.start.y + leftT * dy)
            val rightT = (1f - axis.start.x) / dx
            addIfInside(1f, axis.start.y + rightT * dy)
        }
        if (abs(dy) > 0.000001f) {
            val topT = -axis.start.y / dy
            addIfInside(axis.start.x + topT * dx, 0f)
            val bottomT = (1f - axis.start.y) / dy
            addIfInside(axis.start.x + bottomT * dx, 1f)
        }
        if (points.size < 2) return axis
        val endpoints = points.flatMapIndexed { index, point ->
            points.drop(index + 1).map { other -> point to other }
        }.maxByOrNull { (a, b) -> pixelDistance(a, b, 1f) } ?: return axis
        return FaceAxis(endpoints.first, endpoints.second)
    }

    fun xAtY(axis: FaceAxis, y: Float): Float {
        val dy = axis.end.y - axis.start.y
        if (abs(dy) < 0.000001f) return (axis.start.x + axis.end.x) / 2f
        return axis.start.x + (y - axis.start.y) * (axis.end.x - axis.start.x) / dy
    }

    fun shiftedAxis(axis: FaceAxis, xOffset: Float): FaceAxis = FaceAxis(
        FacePoint(axis.start.x + xOffset, axis.start.y),
        FacePoint(axis.end.x + xOffset, axis.end.y),
    )

    fun setSymmetric(
        nodes: List<BezierNode>,
        enabled: Boolean,
        axis: FaceAxis?,
        imageAspectRatio: Float,
    ): List<BezierNode> {
        if (!enabled || axis == null || nodes.size < 2) return nodes
        val result = nodes.toMutableList()
        for (index in 0 until nodes.size / 2) {
            result[nodes.lastIndex - index] = mirrorNode(nodes[index], axis, imageAspectRatio)
        }
        if (nodes.size % 2 == 1) {
            val centerIndex = nodes.size / 2
            val center = nodes[centerIndex]
            val projected = projectOntoAxis(FacePoint(center.x, center.y), axis, imageAspectRatio)
            val incoming = FacePoint(center.inX, center.inY)
            val outgoing = reflectPoint(incoming, axis, imageAspectRatio)
            result[centerIndex] = center.copy(
                x = projected.x,
                y = projected.y,
                inX = incoming.x,
                inY = incoming.y,
                outX = outgoing.x,
                outY = outgoing.y,
            )
        }
        return result
    }

    fun mirrorNode(node: BezierNode, axis: FaceAxis, imageAspectRatio: Float): BezierNode {
        val anchor = reflectPoint(FacePoint(node.x, node.y), axis, imageAspectRatio)
        val incoming = reflectPoint(FacePoint(node.outX, node.outY), axis, imageAspectRatio)
        val outgoing = reflectPoint(FacePoint(node.inX, node.inY), axis, imageAspectRatio)
        return node.copy(x = anchor.x, y = anchor.y, inX = incoming.x, inY = incoming.y, outX = outgoing.x, outY = outgoing.y)
    }

    fun projectOntoAxis(point: FacePoint, axis: FaceAxis, imageAspectRatio: Float): FacePoint {
        val aspect = imageAspectRatio.coerceAtLeast(0.1f)
        val px = point.x * aspect
        val py = point.y
        val ax = axis.start.x * aspect
        val ay = axis.start.y
        val dx = axis.end.x * aspect - ax
        val dy = axis.end.y - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared < 0.000001f) return axis.start
        val t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared
        return FacePoint(((ax + dx * t) / aspect).coerceIn(0f, 1f), (ay + dy * t).coerceIn(0f, 1f))
    }

    fun reflectPoint(point: FacePoint, axis: FaceAxis, imageAspectRatio: Float): FacePoint {
        val aspect = imageAspectRatio.coerceAtLeast(0.1f)
        val px = point.x * aspect
        val py = point.y
        val ax = axis.start.x * aspect
        val ay = axis.start.y
        val dx = axis.end.x * aspect - ax
        val dy = axis.end.y - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared < 0.000001f) return point
        val t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared
        val projectedX = ax + t * dx
        val projectedY = ay + t * dy
        return FacePoint(((2f * projectedX - px) / aspect).coerceIn(0f, 1f), (2f * projectedY - py).coerceIn(0f, 1f))
    }

    fun insertNodes(nodes: List<BezierNode>, selectedIndex: Int?, symmetric: Boolean): NodeInsertion? {
        if (nodes.size < 2) return null
        val selected = selectedIndex?.takeIf { it in nodes.indices }
        if (!symmetric) {
            val segment = (selected ?: (nodes.size / 2 - 1)).coerceIn(0, nodes.lastIndex - 1)
            return NodeInsertion(splitSegment(nodes, segment), segment + 1)
        }

        // With only one segment, place two distinct nodes at one-third and two-thirds.
        if (nodes.size == 2) {
            val firstSplit = splitSegment(nodes, 0, 1f / 3f)
            return NodeInsertion(splitSegment(firstSplit, 1), if (selected == 1) 2 else 1)
        }

        // For even node counts the middle segment crosses the symmetry axis.
        // Split the two side segments instead of mirroring a midpoint onto itself.
        val lastSideSegment = (nodes.size - 3) / 2
        val segment = selected?.let { minOf(it, nodes.lastIndex - it).coerceAtMost(lastSideSegment) }
            ?: lastSideSegment
        val oppositeSegment = nodes.size - 2 - segment
        // Split the later segment first so the earlier segment's index stays valid.
        val updated = splitSegment(splitSegment(nodes, oppositeSegment), segment)
        val insertedIndex = segment + 1
        val nextSelection = if (selected != null && selected > nodes.lastIndex / 2) {
            updated.lastIndex - insertedIndex
        } else insertedIndex
        return NodeInsertion(updated, nextSelection)
    }

    fun splitSegment(nodes: List<BezierNode>, segmentIndex: Int, fraction: Float = 0.5f): List<BezierNode> {
        if (segmentIndex !in 0 until nodes.lastIndex) return nodes
        require(fraction > 0f && fraction < 1f) { "Split fraction must be inside the segment" }
        val a = nodes[segmentIndex]
        val b = nodes[segmentIndex + 1]
        val p0 = FacePoint(a.x, a.y)
        val p1 = FacePoint(a.outX, a.outY)
        val p2 = FacePoint(b.inX, b.inY)
        val p3 = FacePoint(b.x, b.y)
        fun interpolate(p: FacePoint, q: FacePoint) = FacePoint(p.x + (q.x - p.x) * fraction, p.y + (q.y - p.y) * fraction)
        val q0 = interpolate(p0, p1)
        val q1 = interpolate(p1, p2)
        val q2 = interpolate(p2, p3)
        val r0 = interpolate(q0, q1)
        val r1 = interpolate(q1, q2)
        val s = interpolate(r0, r1)
        val updated = nodes.toMutableList()
        updated[segmentIndex] = a.copy(outX = q0.x, outY = q0.y)
        updated[segmentIndex + 1] = b.copy(inX = q2.x, inY = q2.y)
        updated.add(segmentIndex + 1, BezierNode(s.x, s.y, r0.x, r0.y, r1.x, r1.y, BezierHandleMode.INDEPENDENT))
        return updated
    }

    fun nodesToJson(nodes: List<BezierNode>): String = JSONArray().apply {
        nodes.forEach { node ->
            put(JSONObject().apply {
                put("x", node.x.toDouble())
                put("y", node.y.toDouble())
                put("inX", node.inX.toDouble())
                put("inY", node.inY.toDouble())
                put("outX", node.outX.toDouble())
                put("outY", node.outY.toDouble())
                put("handleMode", node.handleMode.name)
            })
        }
    }.toString()

    fun nodesFromJson(json: String, fallbackHandleMode: BezierHandleMode = BezierHandleMode.MIRRORED): List<BezierNode> = runCatching {
        val array = JSONArray(json)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val mode = runCatching { BezierHandleMode.valueOf(item.optString("handleMode", fallbackHandleMode.name)) }.getOrDefault(fallbackHandleMode)
            BezierNode(
                x = item.getDouble("x").toFloat(),
                y = item.getDouble("y").toFloat(),
                inX = item.getDouble("inX").toFloat(),
                inY = item.getDouble("inY").toFloat(),
                outX = item.getDouble("outX").toFloat(),
                outY = item.getDouble("outY").toFloat(),
                handleMode = mode,
            )
        }
    }.getOrDefault(emptyList())

    fun transformToJson(transform: EditorTransform, appearance: HairlineAppearance = HairlineAppearance()): String = JSONObject().apply {
        put("rotationDegrees", transform.rotationDegrees.toDouble())
        put("scale", transform.scale.toDouble())
        put("translationX", transform.translationX.toDouble())
        put("translationY", transform.translationY.toDouble())
        put("midlineOffset", transform.midlineOffset.toDouble())
        put("hairlineStrokeWidthDp", appearance.strokeWidthDp.toDouble())
        put("hairlineDashed", appearance.dashed)
        put("hairlineNaturalness", appearance.naturalness.toDouble())
    }.toString()

    fun appearanceFromJson(json: String): HairlineAppearance = runCatching {
        val item = JSONObject(json)
        HairlineAppearance(
            strokeWidthDp = item.optDouble("hairlineStrokeWidthDp", 3.0).toFloat().coerceIn(1f, 8f),
            dashed = item.optBoolean("hairlineDashed", false),
            naturalness = item.optDouble("hairlineNaturalness", 0.45).toFloat().coerceIn(0f, 1f),
        )
    }.getOrDefault(HairlineAppearance())

    fun transformFromJson(json: String): EditorTransform = runCatching {
        val item = JSONObject(json)
        EditorTransform(
            rotationDegrees = item.optDouble("rotationDegrees", 0.0).toFloat(),
            scale = item.optDouble("scale", 1.0).toFloat().coerceIn(1f, 4f),
            translationX = item.optDouble("translationX", 0.0).toFloat().coerceIn(-1f, 1f),
            translationY = item.optDouble("translationY", 0.0).toFloat().coerceIn(-1f, 1f),
            midlineOffset = item.optDouble("midlineOffset", 0.0).toFloat().coerceIn(-1f, 1f),
        )
    }.getOrDefault(EditorTransform())

    fun landmarksToJson(landmarks: List<FacialLandmark>): String = JSONArray().apply {
        landmarks.forEach { landmark ->
            put(JSONObject().apply {
                put("name", landmark.name)
                put("x", landmark.x.toDouble())
                put("y", landmark.y.toDouble())
                landmark.confidence?.let { put("confidence", it.toDouble()) }
            })
        }
    }.toString()

    fun landmarksFromJson(json: String): List<FacialLandmark> = runCatching {
        val array = JSONArray(json)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val score = item.opt("confidence")
            FacialLandmark(
                name = item.getString("name"),
                x = item.getDouble("x").toFloat(),
                y = item.getDouble("y").toFloat(),
                confidence = if (score is Number) score.toFloat() else null,
            )
        }
    }.getOrDefault(emptyList())

    private fun nodesFromAnchors(
        anchors: List<FacePoint>,
        imageAspectRatio: Float,
        endpointHookStrength: Float = 0f,
    ): List<BezierNode> {
        val aspect = imageAspectRatio.coerceAtLeast(0.1f)
        return anchors.mapIndexed { index, point ->
            val previous = anchors.getOrElse(index - 1) { point }
            val next = anchors.getOrElse(index + 1) { point }
            val tangentX = ((next.x - previous.x) * aspect) / 2f
            val tangentY = (next.y - previous.y) / 2f
            val magnitude = hypot(tangentX, tangentY).coerceAtLeast(0.0001f)
            val handleScale = min(aspect * 0.05f, pixelDistance(previous, next, aspect) * 0.18f)
            val unitX = tangentX / magnitude
            val unitY = tangentY / magnitude
            val hook = handleScale * endpointHookStrength.coerceIn(0f, 1.2f)
            BezierNode(
                x = point.x,
                y = point.y,
                // The open hairline has one active handle at each endpoint.
                inX = if (index == 0) point.x else point.x - unitX * handleScale / aspect,
                inY = if (index == 0) point.y else if (index == anchors.lastIndex) point.y - hook else point.y - unitY * handleScale,
                outX = if (index == anchors.lastIndex) point.x else if (index == 0) point.x + handleScale * 0.72f / aspect else point.x + unitX * handleScale / aspect,
                outY = if (index == anchors.lastIndex) point.y else if (index == 0) point.y - hook else point.y + unitY * handleScale,
                handleMode = BezierHandleMode.INDEPENDENT,
            )
        }
    }

    private fun pixelDistance(a: FacePoint, b: FacePoint, imageAspectRatio: Float): Float =
        hypot((a.x - b.x) * imageAspectRatio.coerceAtLeast(0.1f), a.y - b.y)

    private fun lineAngleDegrees(axis: FaceAxis, imageAspectRatio: Float): Float =
        Math.toDegrees(atan2((axis.end.y - axis.start.y).toDouble(), ((axis.end.x - axis.start.x) * imageAspectRatio).toDouble())).toFloat()

    private fun normalizeLineRotation(angle: Float): Float = ((angle + 90f) % 180f + 180f) % 180f - 90f

    private fun List<FacialLandmark>.firstByName(name: String): FacialLandmark? = firstOrNull { it.name == name }
}
