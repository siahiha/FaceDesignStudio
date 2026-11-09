package com.example.facedesignstudio.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Size as AndroidSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.facedesignstudio.domain.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class LiveCameraFrame(val bitmap: Bitmap, val capturedAt: Long, val alignment: LiveAlignmentResult)

@Composable
internal fun LivePlanScreen(state: PlannerUiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    var front by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    var showReference by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf<LiveCameraFrame?>(null) }
    var calibrated by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var stableFrames by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val gate = remember { LiveCalibrationGate() }
    val photo = state.pendingPhoto
    val analysis = state.analysis
    val eligible = state.saveComplete && state.savedPlanVersion != null && photo != null && analysis != null && state.nodes.size >= 2
    val running = permission && resumed && eligible
    val view = LocalView.current

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (!resumed) { gate.reset(); frame = null; calibrated = false; ready = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view, running) {
        val previous = view.keepScreenOn
        if (running) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    LaunchedEffect(running) {
        while (running) {
            now = SystemClock.uptimeMillis()
            gate.refresh(now)
            calibrated = gate.calibrated
            ready = gate.ready
            stableFrames = gate.stableFrames
            delay(50)
        }
    }
    // Restarting the camera always discards calibration; callbacks from a disposed session are ignored.
    key(front, retry, running) {
        CameraAnalysisSession(running, front, photo, analysis, state.nodes,
            onReset = { frame = null; gate.reset(); ready = false; calibrated = false; cameraError = false },
            onError = { cameraError = true; gate.reset(); ready = false; calibrated = false },
            onFrame = { incoming ->
                cameraError = false
                now = SystemClock.uptimeMillis()
                val result = if (now - incoming.capturedAt > LiveCalibrationGate.MAX_FRAME_AGE_MS) {
                    incoming.copy(alignment = LiveAlignmentResult(LiveTrackingIssue.STALE_FRAME))
                } else incoming
                calibrated = gate.accept(result.alignment, result.capturedAt, now)
                ready = gate.ready
                stableFrames = gate.stableFrames
                frame = result
            })
    }
    val display = liveFrameDisplay(running, frame?.capturedAt, now)
    val fresh = !cameraError && display == LiveFrameDisplay.CURRENT
    val issue = if (!fresh && frame != null) LiveTrackingIssue.STALE_FRAME else frame?.alignment?.issue
    val issueMessage = if (issue == LiveTrackingIssue.REFERENCE_3D_UNAVAILABLE)
        frame?.alignment?.referenceProblem?.message() ?: issue.message() else issue?.message()
    val matching = fresh && issue == LiveTrackingIssue.NONE
    val overlayMode = if (running && !cameraError) frame?.let { it.alignment.overlayMode(it.capturedAt, now, calibrated) } ?: LiveOverlayMode.HIDDEN else LiveOverlayMode.HIDDEN
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${state.selectedPatient?.firstName.orEmpty()} ${state.selectedPatient?.lastName.orEmpty()} · نسخهٔ ${state.savedPlanVersion ?: "—"}", style = MaterialTheme.typography.titleSmall)
                Text("ردیابی سه‌بعدی آزمایشی · سطح پیشانی تقریبی", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { showHelp = true }) { Text("راهنما") }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF24212C))) {
            if (display != LiveFrameDisplay.EMPTY) frame?.let { current ->
                LiveFrameCanvas(current, mirrored = front, overlayMode = overlayMode, modifier = Modifier.fillMaxSize())
            }
            if (display == LiveFrameDisplay.EMPTY) Text(
                when { !eligible -> "ابتدا طرح را ذخیره یا نسخهٔ ذخیره‌شده را باز کنید."
                    !permission -> "برای نمایش زنده، دسترسی دوربین لازم است."
                    cameraError -> "دوربین یا مدل در دسترس نیست."
                    frame != null -> "منتظر تصویر تازه…"
                    else -> "در حال آماده‌سازی دوربین…" },
                Modifier.align(Alignment.Center).padding(24.dp), color = Color.White,
            )
            if (front && fresh) Text("تصویر آینه‌ای", Modifier.align(Alignment.TopStart).padding(16.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
            if (overlayMode != LiveOverlayMode.HIDDEN) Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), color = if (overlayMode == LiveOverlayMode.CONFIRMED) Color(0xCC183C30) else Color(0xE65C4215), shape = RoundedCornerShape(12.dp)) {
                Text(when (overlayMode) {
                    LiveOverlayMode.ALIGNING -> "راهنمای کمرنگ · زاویه را تنظیم کنید"
                    LiveOverlayMode.CONFIRMED -> "تطبیق تأیید شد"
                    else -> "تطبیق مناسب · منتظر تأیید شما"
                }, Modifier.padding(10.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            if (display != LiveFrameDisplay.EMPTY && !matching && issue != null) Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp), color = Color(0xDD24212C), shape = RoundedCornerShape(12.dp)) {
                Text(if (cameraError) "دریافت تصویر متوقف شد؛ آخرین تصویر نمایش داده می‌شود."
                    else if (!fresh) "منتظر تصویر تازه… خط موقتاً پنهان است."
                    else if (overlayMode == LiveOverlayMode.ALIGNING) "خط کمرنگ فقط برای تنظیم زاویه است؛ هنوز منطبق نیست."
                    else "خط پنهان است: $issueMessage", Modifier.padding(12.dp), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(when {
            cameraError -> "دوربین را دوباره راه‌اندازی کنید یا دوربین دیگر را انتخاب کنید."
            !permission -> "اجازهٔ دوربین را بدهید؛ در صورت رد دائمی از تنظیمات برنامه فعال کنید."
            calibrated && matching -> "خط با گردش سر همراه می‌شود؛ حرکت آرام باشد و پیشانی کامل دیده شود."
            matching && ready -> "جای خط‌چین را بررسی کنید؛ صلیب‌های آبی باید داخل حلقه‌های سفید باشند. سپس تطبیق را تأیید کنید."
            matching -> "پیش‌نمایش خط روی صورت است؛ برای تأیید، ثابت بمانید. پایداری ${stableFrames.coerceAtMost(LiveCalibrationGate.REQUIRED_STABLE_FRAMES)} از ${LiveCalibrationGate.REQUIRED_STABLE_FRAMES}"
            overlayMode == LiveOverlayMode.ALIGNING -> "تطبیق هنوز دقیق نیست؛ ثابت بمانید و صورت را کامل نشان دهید. با انطباق، خط و نقاط پررنگ می‌شوند."
            issueMessage != null -> issueMessage
            else -> "فقط همان مشتری با پیشانی کامل در کادر باشد؛ مدل طرح در حال آماده‌شدن است."
        }, minLines = 3, maxLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Reserve the same space for every tracking state; otherwise the weighted camera
        // viewport resizes (and the face jumps) whenever matching or text wrapping changes.
        Text(if (matching) "اختلاف نقاط مرجع: ${"%.1f".format((frame?.alignment?.residualEyeRatio ?: 0f) * 100)}٪ فاصلهٔ چشم‌ها · معیار تطبیق، نه دقت روی پوست" else " ",
            minLines = 2, maxLines = 2, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { front = !front }, enabled = permission) { Text(if (front) "دوربین پشت" else "دوربین جلو") }
            TextButton(onClick = { showReference = true }, enabled = photo != null) { Text("عکس مرجع") }
            TextButton(onClick = { retry++ }, enabled = permission) { Text("تطبیق دوباره") }
        }
        if (!permission) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioPrimary("اجازهٔ دوربین", { permissionRequest.launch(Manifest.permission.CAMERA) }, Modifier.weight(1f))
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("تنظیمات") }
            }
        } else if (!calibrated) {
            StudioPrimary(if (cameraError) "تلاش دوباره" else if (matching && !ready) "در حال پایدارشدن تطبیق…" else "تأیید جای خط روی صورت", {
                if (cameraError) retry++ else {
                    calibrated = gate.confirm(SystemClock.uptimeMillis())
                    ready = gate.ready
                }
            }, enabled = cameraError || (matching && ready))
        } else {
            StudioPrimary("لغو تأیید و بررسی دوباره", { gate.reset(); calibrated = false; ready = false })
        }
        Spacer(Modifier.height(4.dp))
    }
    if (showHelp) StudioSheet("نمایش طرح روی چهره", { showHelp = false }) {
        Text("۱. نام مشتری و نسخهٔ طرح را بررسی کنید. فقط همان فرد در کادر باشد؛ برنامه هویت فرد را تشخیص نمی‌دهد.")
        Text("۲. با دوربین پشت و نور کافی، چشم‌ها، بینی، چانه و پیشانی کامل دیده شوند. نیازی به تکرار زاویهٔ عکس مرجع نیست؛ گردش آرام سر تا حدود ۴۰ درجه به طرفین و ۳۰ درجه بالا/پایین پشتیبانی می‌شود.")
        Text("۳. هنگام تنظیم زاویه، خط‌چین و نقاط کمرنگ‌اند. صلیب‌های آبیِ مرجع را با حلقه‌های سفیدِ صورت زنده هم‌راستا کنید. با انطباق مناسب، خط و نقاط خودکار پررنگ می‌شوند؛ پس از پایداری و بررسی جای خط، تأیید کنید تا خط پیوسته شود.")
        Text("خط کمرنگ تخمینی و فقط راهنمای تنظیم زاویه است. با کاهش انطباق، خط دوباره کمرنگ و تأیید پاک می‌شود. اگر تطبیق اولیه ممکن نباشد، صورت گم شود، چند چهره دیده شود یا تصویر دیر برسد، خط پنهان می‌شود.")
        Text("عمق صورت از همان دوربین تخمین زده می‌شود و سطح پیشانی تقریبی است؛ این اسکن دقیق یا کالیبراسیون میلی‌متری نیست. اختلاف کم نقاط، دقت خط روی پیشانی را تضمین نمی‌کند. دقت علامت‌گذاری باید مستقل ارزیابی شود.")
        Text("تصویر زنده فقط در حافظه پردازش می‌شود؛ ذخیره یا ارسال نمی‌شود. طرح ذخیره‌شده تغییر نمی‌کند.")
    }
    if (showReference) StudioSheet("عکس مرجع همان طرح", { showReference = false }) {
        val bitmap by produceState<Bitmap?>(null, photo) { value = withContext(Dispatchers.IO) { photo?.let { ImageDecoder.decode(it.originalBytes)?.bitmap } } }
        bitmap?.let { Image(it.asImageBitmap(), "عکس مرجع مشتری", Modifier.fillMaxWidth().height(280.dp), contentScale = ContentScale.Fit) }
        Text("هویت مشتری و شکل طرح را با این عکس بررسی کنید؛ لازم نیست همان زاویه را تکرار کند. بازگشت از این پنل به تأیید مجدد تطبیق نیاز دارد.")
    }
    // Do not leave a confirmed guide running unseen under a modal panel.
    LaunchedEffect(showReference, showHelp) {
        if (showReference || showHelp) { gate.reset(); calibrated = false; ready = false }
    }
}

@Composable
private fun CameraAnalysisSession(
    running: Boolean, front: Boolean, photo: PendingPhoto?, reference: DetectionAnalysis?, nodes: List<BezierNode>,
    onReset: () -> Unit, onError: () -> Unit, onFrame: (LiveCameraFrame) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val rotation = LocalView.current.display?.rotation ?: android.view.Surface.ROTATION_0
    val latestFrame by rememberUpdatedState(onFrame)
    val latestError by rememberUpdatedState(onError)
    DisposableEffect(running, front, owner, photo, reference, nodes, rotation) {
        onReset()
        val alive = AtomicBoolean(running)
        val main = ContextCompat.getMainExecutor(context)
        val worker = Executors.newSingleThreadExecutor()
        var tracker: LiveFaceTracker? = null
        var spatialReference: SpatialReference? = null
        var referenceProblem: ReferencePreparationIssue? = null
        var provider: ProcessCameraProvider? = null
        val analyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetRotation(rotation)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(AndroidSize(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
            .build()
        if (running && photo != null && reference != null) {
            // Prepare on the owning worker before analysis starts. Reconstruct from the
            // original photo for old saved plans; do not change their nodes or database.
            worker.execute {
                if (alive.get()) {
                    spatialReference = runCatching {
                        val image = ImageDecoder.decode(photo.originalBytes)?.bitmap ?: run {
                            referenceProblem = ReferencePreparationIssue.IMAGE_UNREADABLE
                            return@runCatching null
                        }
                        try {
                            LiveFaceTracker(context, referenceMode = true).use { engine ->
                                val face = engine.detect(image, 0)
                                if (face.issue != LiveTrackingIssue.NONE) {
                                    referenceProblem = when (face.issue) {
                                        LiveTrackingIssue.NO_FACE -> ReferencePreparationIssue.FACE_NOT_FOUND
                                        LiveTrackingIssue.MULTIPLE_FACES -> ReferencePreparationIssue.MULTIPLE_FACES
                                        LiveTrackingIssue.MODEL_ERROR -> ReferencePreparationIssue.MODEL_ERROR
                                        else -> ReferencePreparationIssue.INVALID_DATA
                                    }
                                    null
                                } else SpatialFaceAlignment.prepare(
                                    face.mesh, image.width, image.height, nodes, reference.landmarks) {
                                    referenceProblem = ReferencePreparationIssue.fromRejection(it)
                                }
                            }
                        } finally { image.recycle() }
                    }.onFailure { referenceProblem = ReferencePreparationIssue.MODEL_ERROR }.getOrNull()
                    // Model startup must not consume the first camera frame's freshness budget.
                    if (alive.get()) runCatching { tracker = LiveFaceTracker(context) }
                        .onFailure { main.execute { if (alive.get()) latestError() } }
                }
            }
            analyzer.setAnalyzer(worker) { proxy ->
                var frameBitmap: Bitmap? = null
                try {
                    if (alive.get()) {
                        val capturedAt = SystemClock.uptimeMillis()
                        val raw = proxy.toBitmap()
                        val rotated = if (proxy.imageInfo.rotationDegrees == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                            Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }, true).also { if (it !== raw) raw.recycle() }
                        frameBitmap = rotated
                        val engine = tracker ?: LiveFaceTracker(context).also { tracker = it }
                        val face = runCatching { engine.detect(rotated, capturedAt) }
                            .getOrElse { LiveFaceResult(LiveTrackingIssue.MODEL_ERROR) }
                        val alignment = if (face.issue != LiveTrackingIssue.NONE) LiveAlignmentResult(face.issue) else {
                            spatialReference?.let { SpatialFaceAlignment.align(it, face.mesh, rotated.width, rotated.height) }
                                ?: LiveAlignmentResult(LiveTrackingIssue.REFERENCE_3D_UNAVAILABLE, referenceProblem = referenceProblem)
                        }
                        val result = LiveCameraFrame(rotated, capturedAt, alignment)
                        main.execute { if (alive.get()) latestFrame(result) else rotated.recycle() }
                        frameBitmap = null // Ownership passed to the UI; never recycle a displayed bitmap.
                    }
                } catch (_: Exception) {
                    frameBitmap?.recycle()
                    main.execute { if (alive.get()) latestError() }
                } finally { proxy.close() }
            }
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                // Queue behind reference/model preparation before binding. Otherwise an
                // old first proxy may wait through startup but appear newly captured.
                if (alive.get()) worker.execute {
                    main.execute {
                        if (alive.get()) try {
                            val cameraProvider = future.get()
                            provider = cameraProvider
                            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                            if (!cameraProvider.hasCamera(selector)) latestError()
                            else cameraProvider.bindToLifecycle(owner, selector, analyzer)
                        } catch (_: Exception) { latestError() }
                    }
                }
            }, main)
        }
        onDispose {
            alive.set(false)
            analyzer.clearAnalyzer()
            provider?.unbind(analyzer)
            // Close on the owning thread, after any in-flight inference has finished.
            worker.execute { tracker?.close(); tracker = null }
            worker.shutdown()
        }
    }
}

@Composable
private fun LiveFrameCanvas(frame: LiveCameraFrame, mirrored: Boolean, overlayMode: LiveOverlayMode, modifier: Modifier) {
    val bitmap = remember(frame.bitmap) { frame.bitmap.asImageBitmap() }
    Canvas(modifier) {
        // Draw the analyzed image and its matching overlay together, never on a newer PreviewView frame.
        val drawOverlay = overlayMode != LiveOverlayMode.HIDDEN &&
            SystemClock.uptimeMillis() - frame.capturedAt in 0..LiveCalibrationGate.MAX_FRAME_AGE_MS
        val bounds = CoordinateTransformer.fitViewport(size.width, size.height, bitmap.width, bitmap.height)
        fun screen(p: FacePoint) = Offset(bounds.left + p.x * bounds.width, bounds.top + p.y * bounds.height)
        scale(if (mirrored) -1f else 1f, 1f, center) {
            drawImage(bitmap, dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()), dstSize = IntSize(bounds.width.roundToInt(), bounds.height.roundToInt()))
            if (drawOverlay) {
                val nodes = frame.alignment.nodes
                val path = Path()
                frame.alignment.curve.forEachIndexed { i,p ->
                    val point = screen(p)
                    if (i == 0) path.moveTo(point.x,point.y) else path.lineTo(point.x,point.y)
                }
                nodes.firstOrNull()?.let { path.moveTo(screen(FacePoint(it.x, it.y)).x, screen(FacePoint(it.x, it.y)).y) }
                nodes.zipWithNext().forEach { (a, b) ->
                    val c1 = screen(FacePoint(a.outX, a.outY)); val c2 = screen(FacePoint(b.inX, b.inY)); val end = screen(FacePoint(b.x, b.y))
                    path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
                }
                val faint = overlayMode == LiveOverlayMode.ALIGNING
                val opacity = if (faint) .35f else 1f
                val effect = if (overlayMode != LiveOverlayMode.CONFIRMED) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())) else null
                drawPath(path, Color.Black.copy(alpha = .65f * opacity), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, pathEffect = effect))
                val color = if (overlayMode == LiveOverlayMode.CONFIRMED) Color(0xFF5DE8F2) else Color(0xFFFFCF66)
                drawPath(path, color.copy(alpha = opacity), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = effect))
            }
            if (drawOverlay) frame.alignment.expectedMarkers.indices.forEach { i ->
                val expected = screen(frame.alignment.expectedMarkers[i])
                val actual = screen(frame.alignment.actualMarkers[i])
                val opacity = if (overlayMode == LiveOverlayMode.ALIGNING) .35f else 1f
                drawCircle(Color.Black.copy(alpha = opacity), 8.dp.toPx(), actual, style = Stroke(4.dp.toPx()))
                drawCircle(Color.White.copy(alpha = opacity), 8.dp.toPx(), actual, style = Stroke(2.dp.toPx()))
                val radius = 5.dp.toPx()
                drawLine(Color(0xFF5DE8F2).copy(alpha = opacity), expected - Offset(radius, 0f), expected + Offset(radius, 0f), 2.dp.toPx())
                drawLine(Color(0xFF5DE8F2).copy(alpha = opacity), expected - Offset(0f, radius), expected + Offset(0f, radius), 2.dp.toPx())
            }
        }
    }
}

private fun LiveTrackingIssue.message(): String = when (this) {
    LiveTrackingIssue.NONE -> "نقاط چهره پیدا شدند."
    LiveTrackingIssue.NO_FACE -> "چهره پیدا نشد؛ صورت را کامل در کادر بگیرید."
    LiveTrackingIssue.MULTIPLE_FACES -> "بیش از یک چهره دیده شد؛ فقط مشتری در کادر بماند."
    LiveTrackingIssue.INVALID_REFERENCE -> "اطلاعات طرح یا نقاط مرجع ناقص است، یا چشم‌ها در عکس خیلی کوچک‌اند؛ عکس مرجع را در ویرایشگر بررسی کنید."
    LiveTrackingIssue.REFERENCE_NOT_FRONTAL -> "زاویهٔ صورت در عکس مرجع برای تطبیق مناسب نیست؛ عکس روبه‌رو لازم است."
    LiveTrackingIssue.INVALID_LANDMARKS -> "نقاط چهره کامل دیده نمی‌شوند؛ چشم‌ها، بینی و چانه را نپوشانید."
    LiveTrackingIssue.FACE_TOO_SMALL -> "دوربین را کمی نزدیک‌تر کنید؛ پیشانی کامل در کادر بماند."
    LiveTrackingIssue.POSE_MISMATCH -> "زاویهٔ سر با عکس مرجع فرق دارد؛ چرخش به طرفین یا بالا و پایین را کم کنید."
    LiveTrackingIssue.POOR_FIT -> "تطبیق کافی نیست؛ زاویه، نور و عکس مرجع را بررسی کنید."
    LiveTrackingIssue.OUT_OF_FRAME -> "بخشی از طرح بیرون کادر است؛ دوربین را کمی عقب ببرید."
    LiveTrackingIssue.MODEL_ERROR -> "تحلیل تصویر ناموفق بود؛ دوباره تلاش کنید."
    LiveTrackingIssue.STALE_FRAME -> "تصویر به‌روز نیست؛ طرح تا دریافت تصویر تازه پنهان است."
    LiveTrackingIssue.REFERENCE_3D_UNAVAILABLE -> "سطح سه‌بعدی این طرح قابل برآورد نیست؛ عکس مرجع و قرارگیری خط روی پیشانی را بررسی کنید."
    LiveTrackingIssue.POSE_OUT_OF_RANGE -> "چرخش سر زیاد است؛ کمی به سمت دوربین برگردید. لازم نیست زاویهٔ عکس مرجع را تکرار کنید."
}

private fun ReferencePreparationIssue.message(): String = when (this) {
    ReferencePreparationIssue.IMAGE_UNREADABLE -> "عکس ذخیره‌شده باز نشد؛ طرح را دوباره باز کنید."
    ReferencePreparationIssue.FACE_NOT_FOUND -> "چهره در عکس ذخیره‌شده پیدا نشد؛ عکس واضح‌تر با صورت کامل لازم است."
    ReferencePreparationIssue.MULTIPLE_FACES -> "در عکس مرجع چند چهره دیده می‌شود؛ عکس فقط از مشتری لازم است."
    ReferencePreparationIssue.POSE_OUT_OF_RANGE -> "خم‌شدن یا چرخش صورت در عکس ذخیره‌شده بیش از محدودهٔ ۴۰ درجه است؛ عکس با زاویهٔ کمتر لازم است."
    ReferencePreparationIssue.LANDMARK_MISMATCH -> "نقاط تشخیص‌داده‌شده با نقاط طرح ذخیره‌شده تطابق ندارند؛ عکس را در ویرایشگر دوباره تحلیل و طرح را بررسی کنید."
    ReferencePreparationIssue.CURVE_OUTSIDE_FOREHEAD -> "بخشی از خط خارج از محدودهٔ قابل برآورد پیشانی است؛ جای خط را در ویرایشگر بررسی کنید."
    ReferencePreparationIssue.SURFACE_UNAVAILABLE -> "عمق پیشانی در عکس ذخیره‌شده قابل برآورد نیست؛ عکس واضح‌تر با پیشانی کامل لازم است."
    ReferencePreparationIssue.INVALID_DATA -> "نقاط عکس یا خط ذخیره‌شده ناقص‌اند؛ طرح را در ویرایشگر بررسی کنید."
    ReferencePreparationIssue.MODEL_ERROR -> "مدل عکس مرجع آماده نشد؛ «تطبیق دوباره» را بزنید."
}
