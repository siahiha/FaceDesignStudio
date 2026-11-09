package com.example.facedesignstudio.domain

import kotlin.math.*

/** Relative MediaPipe depth, not a scan or a metric measurement. z uses the x scale. */
data class MeshPoint(val x: Float, val y: Float, val z: Float) {
    operator fun plus(p: MeshPoint) = MeshPoint(x + p.x, y + p.y, z + p.z)
    operator fun minus(p: MeshPoint) = MeshPoint(x - p.x, y - p.y, z - p.z)
    operator fun times(s: Float) = MeshPoint(x * s, y * s, z * s)
    fun dot(p: MeshPoint) = x * p.x + y * p.y + z * p.z
    fun length() = sqrt(dot(this))
    fun finite() = x.isFinite() && y.isFinite() && z.isFinite()
}

data class SpatialTransform(val rotation: List<Float>, val scale: Float, val translation: MeshPoint) {
    fun rotate(p: MeshPoint) = MeshPoint(
        rotation[0]*p.x + rotation[1]*p.y + rotation[2]*p.z,
        rotation[3]*p.x + rotation[4]*p.y + rotation[5]*p.z,
        rotation[6]*p.x + rotation[7]*p.y + rotation[8]*p.z,
    )
    fun map(p: MeshPoint) = rotate(p) * scale + translation
}

class SpatialReference internal constructor(
    internal val mesh: List<MeshPoint>,
    internal val curve: List<MeshPoint>,
)

enum class ReferencePreparationIssue {
    IMAGE_UNREADABLE, FACE_NOT_FOUND, MULTIPLE_FACES, POSE_OUT_OF_RANGE,
    LANDMARK_MISMATCH, CURVE_OUTSIDE_FOREHEAD, SURFACE_UNAVAILABLE, INVALID_DATA, MODEL_ERROR;

    companion object {
        fun fromRejection(reason: String) = when (reason) {
            "reference_pose" -> POSE_OUT_OF_RANGE
            "reference_mismatch" -> LANDMARK_MISMATCH
            "curve_outside_image", "curve_outside_forehead" -> CURVE_OUTSIDE_FOREHEAD
            "degenerate_surface", "poor_surface_fit", "unsupported_depth" -> SURFACE_UNAVAILABLE
            else -> INVALID_DATA
        }
    }
}

/** Weak-perspective 3D registration plus a bounded, approximate forehead surface.
 * No per-frame neural inference is added. The saved curve is sampled before lifting;
 * projecting just its Bezier handles would not preserve a curved surface attachment.
 */
object SpatialFaceAlignment {
    // Stable upper/mid-face points; exclude moving lips, irises and jaw from the fit.
    internal val fitIndices = listOf(4, 6, 10, 33, 54, 67, 93, 103, 127, 133, 151, 168, 234,
        263, 284, 297, 323, 332, 356, 362, 454)
    internal val markerIndices = listOf(473, 468, 168, 1, 127, 356, 152)
    // Upper-face vertices (canonical y > 3.5), from MediaPipe's canonical face topology.
    internal val foreheadIndices = listOf(8,9,10,21,46,52,53,54,55,63,65,66,67,68,69,70,71,
        103,104,105,107,108,109,139,151,162,221,222,223,224,251,276,282,283,284,285,293,
        295,296,297,298,299,300,301,332,333,334,336,337,338,368,389,441,442,443,444)
    private val requiredIndices = (fitIndices + markerIndices + foreheadIndices).distinct()
    const val MAX_YAW_DEGREES = 40f
    const val MAX_PITCH_DEGREES = 30f
    // A still reference can show more forehead while the head is tilted down.
    // It is reconstructed once and still must pass surface/depth/landmark checks.
    // Keep the narrower live limit where tracking and occlusion can change each frame.
    const val MAX_REFERENCE_PITCH_DEGREES = 40f
    private const val SAMPLES_PER_SEGMENT = 24

    private fun pixelMesh(mesh: List<MeshPoint>, width: Int, height: Int): List<MeshPoint>? {
        if (width <= 0 || height <= 0 || requiredIndices.any { i ->
                mesh.getOrNull(i)?.let { !it.finite() || it.x !in 0f..1f || it.y !in 0f..1f } != false
            }) return null
        val aspect = width.toFloat() / height
        return mesh.map { MeshPoint(it.x * aspect, it.y, it.z * aspect) }
    }

    /** Rebuild once from the original photo, so existing saved plans need no migration. */
    fun prepare(mesh: List<MeshPoint>, width: Int, height: Int, nodes: List<BezierNode>,
                savedLandmarks: List<FacialLandmark>, onRejected: (String) -> Unit = {}): SpatialReference? {
        fun reject(reason: String): SpatialReference? { onRejected(reason); return null }
        val source = pixelMesh(mesh, width, height) ?: return reject("invalid_mesh")
        if (!supportedPose(source, MAX_REFERENCE_PITCH_DEGREES)) return reject("reference_pose")
        if (nodes.size !in 2..12) return reject("invalid_nodes")
        val eyes = (source[473] - source[468]).length()
        if (eyes < .03f * minOf(width.toFloat()/height, 1f)) return reject("small_reference")
        // Ensure that the re-detected face is the face used by this saved plan.
        val saved = savedLandmarks.associateBy { it.name }
        val aspect = width.toFloat()/height
        val referenceErrors = markerIndices.mapIndexed { i, index ->
            val p = saved[LiveAlignment.anchorNames[i]] ?: return reject("missing_saved_points")
            if (!p.x.isFinite() || !p.y.isFinite()) return reject("invalid_saved_points")
            hypot(source[index].x - p.x*aspect, source[index].y - p.y) / eyes
        }
        if (referenceErrors.max() > .25f || sqrt(referenceErrors.sumOf { (it*it).toDouble() }/referenceErrors.size) > .12) return reject("reference_mismatch")
        val origin = (source[473] + source[468]) * .5f
        val forehead = foreheadIndices.map { (source[it] - origin) * (1f/eyes) }
        fun terms(x: Double, y: Double) = doubleArrayOf(1.0, x, y, x*x, x*y, y*y)
        val normal = Array(6) { DoubleArray(6) }
        val rhs = DoubleArray(6)
        forehead.forEach { p ->
            val row = terms(p.x.toDouble(), p.y.toDouble())
            for (i in 0..5) {
                rhs[i] += row[i]*p.z
                for (j in 0..5) normal[i][j] += row[i]*row[j]
            }
        }
        // Small curvature regularization avoids unstable extrapolation above the face mesh.
        for (i in 3..5) normal[i][i] += .001
        val coefficients = solve(normal, rhs) ?: return reject("degenerate_surface")
        fun depth(x: Float, y: Float): Float = terms(x.toDouble(), y.toDouble())
            .zip(coefficients.toList()).sumOf { (a,b) -> a*b }.toFloat()
        val surfaceError = sqrt(forehead.sumOf { p -> (depth(p.x,p.y)-p.z).toDouble().pow(2) } / forehead.size)
        if (!surfaceError.isFinite() || surfaceError > .15) return reject("poor_surface_fit")
        val minX = forehead.minOf { it.x } - .35f; val maxX = forehead.maxOf { it.x } + .35f
        val minY = forehead.minOf { it.y } - .75f; val maxY = forehead.maxOf { it.y } + .25f
        val minZ = forehead.minOf { it.z } - .5f; val maxZ = forehead.maxOf { it.z } + .75f
        fun extendedDepth(x: Float, y: Float): Float {
            // The mesh ends below some hairlines. Beyond its measured domain use the
            // boundary tangent, not a quadratic that accelerates into implausible depth.
            // Spatial and depth bounds still reject unsupported extensions.
            val bx=x.coerceIn(minX+.35f,maxX-.35f)
            val by=y.coerceIn(minY+.75f,maxY-.25f)
            val dx=coefficients[1]+2*coefficients[3]*bx+coefficients[4]*by
            val dy=coefficients[2]+coefficients[4]*bx+2*coefficients[5]*by
            return (depth(bx,by)+(x-bx)*dx+(y-by)*dy).toFloat()
        }
        val lifted = mutableListOf<MeshPoint>()
        nodes.zipWithNext().forEachIndexed { segment, (a,b) ->
            if (listOf(a.x,a.y,a.outX,a.outY,b.inX,b.inY,b.x,b.y).any { !it.isFinite() }) return reject("invalid_curve")
            for (step in (if (segment == 0) 0 else 1)..SAMPLES_PER_SEGMENT) {
                val t = step.toFloat()/SAMPLES_PER_SEGMENT; val u = 1f-t
                fun cubic(p:Float,c:Float,d:Float,q:Float) = u*u*u*p + 3*u*u*t*c + 3*u*t*t*d + t*t*t*q
                val x = cubic(a.x,a.outX,b.inX,b.x); val y = cubic(a.y,a.outY,b.inY,b.y)
                if (x !in 0f..1f || y !in 0f..1f) return reject("curve_outside_image")
                val localX = (x*aspect-origin.x)/eyes; val localY = (y-origin.y)/eyes
                if (localX !in minX..maxX || localY !in minY..maxY) return reject("curve_outside_forehead")
                val z = extendedDepth(localX, localY)
                if (!z.isFinite() || z !in minZ..maxZ) return reject("unsupported_depth")
                lifted.add(MeshPoint(x*aspect, y, origin.z + z*eyes))
            }
        }
        return SpatialReference(source, lifted)
    }

    fun align(reference: SpatialReference, mesh: List<MeshPoint>, width: Int, height: Int): LiveAlignmentResult {
        fun reject(issue: LiveTrackingIssue) = LiveAlignmentResult(issue)
        val current = pixelMesh(mesh, width, height) ?: return reject(LiveTrackingIssue.INVALID_LANDMARKS)
        val aspect = width.toFloat()/height
        val eyes = hypot(current[473].x-current[468].x, current[473].y-current[468].y)
        if (eyes < .12f*minOf(aspect,1f)) return reject(LiveTrackingIssue.FACE_TOO_SMALL)
        if (!supportedPose(current)) return reject(LiveTrackingIssue.POSE_OUT_OF_RANGE)
        val p = fitIndices.map { reference.mesh[it] }; val q = fitIndices.map { current[it] }
        val fit = fitRigid(p,q) ?: return reject(LiveTrackingIssue.POOR_FIT)
        val errors = p.zip(q) { a,b ->
            val projected = fit.map(a)
            hypot(projected.x-b.x,projected.y-b.y)/eyes
        }
        val depthRms = sqrt(p.zip(q).sumOf { (a,b) -> ((fit.map(a)-b).length()/eyes).toDouble().pow(2) }/p.size).toFloat()
        val rms = sqrt(errors.sumOf { (it*it).toDouble() }/errors.size).toFloat()
        if (!rms.isFinite() || !depthRms.isFinite() || rms > LiveAlignment.MAX_GUIDANCE_RMS_EYE_RATIO ||
            errors.max() > LiveAlignment.MAX_GUIDANCE_POINT_EYE_RATIO || depthRms > .3f) return reject(LiveTrackingIssue.POOR_FIT)
        val issue = if (rms <= LiveAlignment.MAX_RMS_EYE_RATIO && errors.max() <= LiveAlignment.MAX_POINT_EYE_RATIO && depthRms <= .12f)
            LiveTrackingIssue.NONE else LiveTrackingIssue.POOR_FIT
        fun project(p: MeshPoint) = FacePoint(p.x/aspect,p.y)
        val curve = reference.curve.map { project(fit.map(it)) }
        if (curve.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) return reject(LiveTrackingIssue.OUT_OF_FRAME)
        return LiveAlignmentResult(issue = issue, expectedMarkers = markerIndices.map { project(fit.map(reference.mesh[it])) },
            actualMarkers = markerIndices.map { project(current[it]) }, residualEyeRatio = rms, eyeDistance = eyes,
            frameAspect = aspect, guidanceAvailable = issue != LiveTrackingIssue.NONE, spatialRegistration = fit, curve = curve)
    }

    private fun supportedPose(mesh: List<MeshPoint>, maxPitch: Float = MAX_PITCH_DEGREES): Boolean {
        val right = mesh[263]-mesh[33]; val down = mesh[152]-mesh[10]
        val n = MeshPoint(right.y*down.z-right.z*down.y, right.z*down.x-right.x*down.z, right.x*down.y-right.y*down.x)
        if (n.length() < 1e-6f || n.z <= 0f) return false // Never accept mirrored/back-facing geometry.
        val yaw = Math.toDegrees(atan2(n.x.toDouble(),n.z.toDouble()))
        val pitch = Math.toDegrees(atan2(n.y.toDouble(),n.z.toDouble()))
        return abs(yaw) <= MAX_YAW_DEGREES && abs(pitch) <= maxPitch
    }

    /** Horn's proper rotation (no shear/reflection), from the largest eigenvector of a 4x4 symmetric matrix. */
    internal fun fitRigid(source: List<MeshPoint>, target: List<MeshPoint>): SpatialTransform? {
        if (source.size != target.size || source.size < 4 || (source+target).any { !it.finite() }) return null
        fun center(points:List<MeshPoint>) = points.fold(MeshPoint(0f,0f,0f)) { a,p -> a+p }*(1f/points.size)
        val pc=center(source); val qc=center(target)
        val p=source.map { it-pc }; val q=target.map { it-qc }
        val s=Array(3) { DoubleArray(3) }
        p.zip(q).forEach { (a,b) ->
            val av=listOf(a.x,a.y,a.z); val bv=listOf(b.x,b.y,b.z)
            for (i in 0..2) for (j in 0..2) s[i][j] += av[i].toDouble()*bv[j]
        }
        val xx=s[0][0]; val xy=s[0][1]; val xz=s[0][2]
        val yx=s[1][0]; val yy=s[1][1]; val yz=s[1][2]
        val zx=s[2][0]; val zy=s[2][1]; val zz=s[2][2]
        val n=arrayOf(doubleArrayOf(xx+yy+zz,yz-zy,zx-xz,xy-yx),
            doubleArrayOf(yz-zy,xx-yy-zz,xy+yx,zx+xz),
            doubleArrayOf(zx-xz,xy+yx,-xx+yy-zz,yz+zy),
            doubleArrayOf(xy-yx,zx+xz,yz+zy,-xx-yy+zz))
        val v=Array(4) { i -> DoubleArray(4) { j -> if(i==j) 1.0 else 0.0 } }
        repeat(40) {
            var a=0; var b=1
            for (i in 0..3) for(j in i+1..3) if(abs(n[i][j])>abs(n[a][b])) { a=i;b=j }
            if(abs(n[a][b])>1e-12) {
                val angle=.5*atan2(2*n[a][b],n[b][b]-n[a][a]); val c=cos(angle);val sn=sin(angle)
                val aa=n[a][a];val bb=n[b][b];val ab=n[a][b]
                for(k in 0..3) if(k!=a && k!=b) {
                    val ka=n[k][a];val kb=n[k][b]
                    n[k][a]=c*ka-sn*kb;n[a][k]=n[k][a]
                    n[k][b]=sn*ka+c*kb;n[b][k]=n[k][b]
                }
                n[a][a]=c*c*aa-2*sn*c*ab+sn*sn*bb;n[b][b]=sn*sn*aa+2*sn*c*ab+c*c*bb
                n[a][b]=0.0;n[b][a]=0.0
                for(k in 0..3) { val ka=v[k][a];val kb=v[k][b];v[k][a]=c*ka-sn*kb;v[k][b]=sn*ka+c*kb }
            }
        }
        val eigen=(0..3).maxBy { n[it][it] }
        val w=v[0][eigen];val x=v[1][eigen];val y=v[2][eigen];val z=v[3][eigen]
        val rotation=listOf(1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w),2*(x*y+z*w),1-2*(x*x+z*z),
            2*(y*z-x*w),2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)).map { it.toFloat() }
        val unit=SpatialTransform(rotation,1f,MeshPoint(0f,0f,0f))
        val norm=p.sumOf { it.dot(it).toDouble() }
        if(norm<1e-8) return null
        val scale=(p.zip(q).sumOf { (a,b) -> unit.rotate(a).dot(b).toDouble() }/norm).toFloat()
        if(!scale.isFinite() || scale<=0f) return null
        return SpatialTransform(rotation,scale,qc-unit.rotate(pc)*scale)
    }

    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n=b.size;val m=Array(n) { i -> a[i].copyOf(n+1).also { it[n]=b[i] } }
        for(i in 0 until n) {
            val pivot=(i until n).maxBy { abs(m[it][i]) }
            if(abs(m[pivot][i])<1e-10) return null
            val temp=m[i];m[i]=m[pivot];m[pivot]=temp
            val divisor=m[i][i];for(j in i..n) m[i][j]/=divisor
            for(k in 0 until n) if(k!=i) { val f=m[k][i];for(j in i..n) m[k][j]-=f*m[i][j] }
        }
        return DoubleArray(n) { m[it][n] }.takeIf { values -> values.all { it.isFinite() } }
    }
}
