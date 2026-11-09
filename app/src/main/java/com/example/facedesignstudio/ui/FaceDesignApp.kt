package com.example.facedesignstudio.ui

import android.Manifest
import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.toMutableStateList
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.facedesignstudio.data.local.HairlinePlanVersionEntity
import com.example.facedesignstudio.data.local.PatientEntity
import com.example.facedesignstudio.domain.BezierHandleMode
import com.example.facedesignstudio.domain.BezierNode
import com.example.facedesignstudio.domain.DetectionRejection
import com.example.facedesignstudio.domain.EditorTransform
import com.example.facedesignstudio.domain.HairlineGeometry
import com.example.facedesignstudio.domain.FacePoint
import com.example.facedesignstudio.domain.CoordinateTransformer
import com.example.facedesignstudio.domain.ImageViewport
import com.example.facedesignstudio.domain.ImageDecoder
import com.example.facedesignstudio.domain.HairlineProfile
import com.example.facedesignstudio.domain.HairlineReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class PlannerScreen { HOME, PATIENT_FORM, PATIENT_DETAIL, PHOTO_SOURCE, CAMERA, PHOTO_REVIEW, EDITOR, LIVE_PLAN, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceDesignApp(viewModel: PlannerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val screens = rememberSaveable(saver = listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<PlannerScreen>, String>(
        save = { stack -> stack.map { it.name } },
        restore = { names -> names.map(PlannerScreen::valueOf).toMutableStateList() },
    )) { androidx.compose.runtime.mutableStateListOf(PlannerScreen.HOME) }
    var editingPatientId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var annotatedImageExport by remember { mutableStateOf(true) }
    val go: (PlannerScreen) -> Unit = { next -> if (screens.lastOrNull() != next) screens.add(next) }
    var confirmLeave by remember { mutableStateOf(false) }
    val leaveEditor: () -> Unit = {
        while (screens.lastOrNull() in listOf(PlannerScreen.EDITOR, PlannerScreen.PHOTO_REVIEW, PlannerScreen.PHOTO_SOURCE)) screens.removeAt(screens.lastIndex)
        if (screens.isEmpty()) screens.add(PlannerScreen.HOME)
        viewModel.clearPhoto()
    }
    val back: () -> Unit = {
        if (!state.busy) {
            if (screens.lastOrNull() == PlannerScreen.EDITOR) {
                if (state.analysis != null && !state.saveComplete) confirmLeave = true else leaveEditor()
            } else if (screens.size > 1) screens.removeAt(screens.lastIndex)
        }
    }
    val current = screens.lastOrNull() ?: PlannerScreen.HOME

    // Large photo data lives in the ViewModel; after process death return to a valid route.
    LaunchedEffect(Unit) {
        if (state.selectedPatient == null && current in listOf(PlannerScreen.PATIENT_DETAIL, PlannerScreen.PHOTO_SOURCE, PlannerScreen.PHOTO_REVIEW, PlannerScreen.CAMERA, PlannerScreen.EDITOR, PlannerScreen.LIVE_PLAN)) {
            screens.clear(); screens.add(PlannerScreen.HOME)
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) go(PlannerScreen.CAMERA)
        else scope.launch { snackbar.showSnackbar("برای عکس‌برداری، دسترسی دوربین لازم است.") }
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val data = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                withContext(Dispatchers.Main) {
                    if (data != null) {
                        viewModel.selectPhoto(data, "GALLERY", context.contentResolver.getType(uri))
                        go(PlannerScreen.PHOTO_REVIEW)
                    } else scope.launch { snackbar.showSnackbar("تصویر انتخاب‌شده قابل خواندن نیست.") }
                }
            }
        }
    }
    val imageExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) {
            val photo = state.pendingPhoto
            val analysis = state.analysis
            if (photo != null && analysis != null) {
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        val decoded = ImageDecoder.decode(photo.originalBytes) ?: return@withContext null
                        var rendered: Bitmap? = null
                        try {
                            rendered = PlanImageExporter.render(
                                decoded.bitmap,
                                analysis,
                                state.nodes,
                                state.transform,
                                state.hairlineAppearance,
                                includeReferences = annotatedImageExport,
                                includeControls = annotatedImageExport,
                            )
                            java.io.ByteArrayOutputStream().use { output ->
                                rendered.compress(Bitmap.CompressFormat.PNG, 100, output)
                                output.toByteArray()
                            }
                        } finally {
                            rendered?.recycle()
                            decoded.bitmap.recycle()
                        }
                    }
                    val saved = withContext(Dispatchers.IO) { bytes?.let { data -> runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: error("no output") }.isSuccess } == true }
                    snackbar.showSnackbar(if (saved) "تصویر خروجی ذخیره شد." else "ساخت یا ذخیرهٔ تصویر ناموفق بود.")
                }
            }
        }
    }

    BackHandler(enabled = screens.size > 1 && current != PlannerScreen.CAMERA) { back() }
    BackHandler(enabled = current == PlannerScreen.CAMERA) { back() }

    LaunchedEffect(state.messageKey) {
        val key = state.messageKey ?: return@LaunchedEffect
        val message = when (key) {
            "settings_saved" -> "تنظیمات ذخیره شد"
            "patient_saved" -> "اطلاعات بیمار ذخیره شد"
            "patient_deleted" -> "بیمار حذف شد"
            "patient_code_exists" -> "کد بیمار تکراری است یا ذخیره انجام نشد"
            "plan_saved" -> "طرح به‌عنوان نسخهٔ ${state.savedPlanVersion} ذخیره شد"
            "plan_deleted" -> "نسخهٔ انتخاب‌شدهٔ طرح حذف شد"
            "plan_delete_failed" -> "حذف طرح انجام نشد؛ دوباره تلاش کنید"
            "save_failed" -> "ذخیرهٔ طرح انجام نشد"
            "plan_load_failed" -> "بارگذاری طرح ذخیره‌شده انجام نشد"
            else -> key
        }
        snackbar.showSnackbar(message)
        viewModel.clearMessage()
    }

    LaunchedEffect(state.analysis, current) {
        if (state.analysis != null && current == PlannerScreen.PHOTO_REVIEW) {
            go(PlannerScreen.EDITOR)
        }
    }

    val title = when (current) {
        PlannerScreen.HOME -> "چهره‌پرداز"
        PlannerScreen.PATIENT_FORM -> if (editingPatientId == null) "ثبت بیمار" else "ویرایش بیمار"
        PlannerScreen.PATIENT_DETAIL -> "پروندهٔ بیمار"
        PlannerScreen.PHOTO_SOURCE -> "انتخاب تصویر"
        PlannerScreen.CAMERA -> "عکس‌برداری"
        PlannerScreen.PHOTO_REVIEW -> "بررسی تصویر"
        PlannerScreen.EDITOR -> "ویرایش طرح"
        PlannerScreen.LIVE_PLAN -> "طرح روی دوربین زنده"
        PlannerScreen.SETTINGS -> "تنظیمات تشخیص"
    }

    CompositionLocalProviderRtl {
        if (confirmLeave) AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("از طراحی خارج می‌شوید؟") },
            text = { Text("تغییرات این طرح هنوز ذخیره نشده‌اند.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; leaveEditor() }) { Text("خروج بدون ذخیره") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("ادامهٔ طراحی") } },
        )
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    navigationIcon = {
                        if (screens.size > 1) IconButton(onClick = back, modifier = Modifier.semantics { contentDescription = "بازگشت" }) { StudioIcon(StudioSymbol.Back) }
                    },
                    actions = {
                        if (current == PlannerScreen.HOME) {
                            IconButton(onClick = { go(PlannerScreen.SETTINGS) }, modifier = Modifier.semantics { contentDescription = "تنظیمات" }) { StudioIcon(StudioSymbol.Settings) }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            when (current) {
                PlannerScreen.HOME -> HomeScreen(
                    state = state,
                    onSearch = viewModel::setSearchQuery,
                    onAdd = { editingPatientId = null; go(PlannerScreen.PATIENT_FORM) },
                    onSettings = { go(PlannerScreen.SETTINGS) },
                    onPatient = { patient -> viewModel.selectPatient(patient.id); go(PlannerScreen.PATIENT_DETAIL) },
                    modifier = Modifier.padding(padding),
                )
                PlannerScreen.PATIENT_FORM -> PatientFormScreen(
                    existing = editingPatientId?.let { id -> state.patients.firstOrNull { it.id == id } },
                    onCancel = back,
                    onSave = { code, first, last, age, sex, phone, notes ->
                        viewModel.savePatient(editingPatientId, code, first, last, age, sex, phone, notes) { id ->
                            viewModel.selectPatient(id)
                            screens.clear(); screens.add(PlannerScreen.HOME); screens.add(PlannerScreen.PATIENT_DETAIL)
                        }
                    },
                    modifier = Modifier.padding(padding),
                )
                PlannerScreen.PATIENT_DETAIL -> PatientDetailScreen(
                    state = state,
                    onNewPlan = { viewModel.clearPhoto(); go(PlannerScreen.PHOTO_SOURCE) },
                    onEditPatient = { editingPatientId = state.selectedPatient?.id; go(PlannerScreen.PATIENT_FORM) },
                    onPlan = { plan -> viewModel.loadPlan(plan.snapshotId) { go(PlannerScreen.EDITOR) } },
                    onDeletePlan = { plan -> viewModel.deletePlan(plan.snapshotId) },
                    onDeletePatient = {
                        state.selectedPatient?.id?.let { viewModel.deletePatient(it) }
                        screens.clear()
                        screens.add(PlannerScreen.HOME)
                    },
                    modifier = Modifier.padding(padding),
                )
                PlannerScreen.PHOTO_SOURCE -> PhotoSourceScreen(
                    onCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onGallery = { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.padding(padding),
                )
                PlannerScreen.CAMERA -> CameraCaptureScreen(
                    onCaptured = { bytes -> viewModel.selectPhoto(bytes, "CAMERA", "image/jpeg"); back(); go(PlannerScreen.PHOTO_REVIEW) },
                    onCancel = back,
                    modifier = Modifier.padding(padding),
                )
                PlannerScreen.PHOTO_REVIEW -> PhotoReviewScreen(
                    state = state,
                    onAnalyze = viewModel::analyzePhoto,
                    onReference = { viewModel.setPlanningOptions(reference = it) },
                    onProfile = { viewModel.setPlanningOptions(profile = it) },
                    onCustomRise = { viewModel.setPlanningOptions(customRiseRatio = it) },
                    onThresholdChange = viewModel::saveThreshold,
                    onRetake = { viewModel.clearPhoto(); back() },
                    modifier = Modifier.padding(padding),
                )
                PlannerScreen.EDITOR -> EditorScreen(
                    state = state,
                    viewModel = viewModel,
                    onSave = viewModel::savePlan,
                    onLivePlan = { if (state.saveComplete && state.savedPlanVersion != null && !state.busy) go(PlannerScreen.LIVE_PLAN) },
                    onExportImage = { annotated ->
                        annotatedImageExport = annotated
                        val code = state.selectedPatient?.patientCode?.replace(Regex("[^A-Za-z0-9_-]"), "_") ?: "patient"
                        val version = state.savedPlanVersion ?: 1
                        val kind = if (annotated) "annotated" else "preview"
                        imageExportPicker.launch("FaceDesign_${kind}_${code}_v${version}.png")
                    },
                    modifier = Modifier.padding(padding),
                )
                PlannerScreen.LIVE_PLAN -> LivePlanScreen(state, Modifier.padding(padding))
                PlannerScreen.SETTINGS -> SettingsScreen(
                    currentThreshold = state.configuredThresholdPercent,
                    onSave = viewModel::saveThreshold,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun CompositionLocalProviderRtl(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
}

@Composable
private fun HomeScreen(
    state: PlannerUiState,
    onSearch: (String) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onPatient: (PatientEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query = state.patientCodeSearch.trim()
    val patients = state.patients.filter {
        query.isBlank() || it.patientCode.contains(query, true) || "${it.firstName} ${it.lastName}".contains(query, true)
    }
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StudioHeading("هر چهره، یک طراحی", "برای شروع، پروندهٔ بیمار را انتخاب کنید.")
        OutlinedTextField(
            value = state.patientCodeSearch, onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(), placeholder = { Text("نام یا کد پرونده") },
            leadingIcon = { StudioIcon(StudioSymbol.Search) },
            shape = RoundedCornerShape(18.dp), singleLine = true,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("پرونده‌ها", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("${patients.size} بیمار", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)) {
            if (patients.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        StudioIcon(StudioSymbol.Folder, Modifier.padding(24.dp).size(32.dp), MaterialTheme.colorScheme.primary)
                    }
                    Text(if (query.isBlank()) "اولین پرونده را بسازید" else "پرونده‌ای پیدا نشد", style = MaterialTheme.typography.titleMedium)
                    Text(if (query.isBlank()) "طراحی خط رویش از اینجا شروع می‌شود." else "نام یا کد دیگری را جست‌وجو کنید.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(patients, key = { it.id }) { patient ->
                Surface(onClick = { onPatient(patient) }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Text(patient.firstName.take(1), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${patient.firstName} ${patient.lastName}", style = MaterialTheme.typography.titleMedium)
                            Text("پروندهٔ ${patient.patientCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        StudioPrimary("ثبت بیمار جدید", onAdd, Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun PatientFormScreen(
    existing: PatientEntity?, onCancel: () -> Unit,
    onSave: (String, String, String, Int?, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by rememberSaveable(existing?.id) { mutableStateOf(existing?.patientCode.orEmpty()) }
    var first by rememberSaveable(existing?.id) { mutableStateOf(existing?.firstName.orEmpty()) }
    var last by rememberSaveable(existing?.id) { mutableStateOf(existing?.lastName.orEmpty()) }
    var age by rememberSaveable(existing?.id) { mutableStateOf(existing?.age?.toString().orEmpty()) }
    val sexOptions = listOf("زن", "مرد", "نامشخص")
    var sex by rememberSaveable(existing?.id) { mutableStateOf(existing?.sex?.takeIf { it in sexOptions } ?: "نامشخص") }
    var phone by rememberSaveable(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }
    var notes by rememberSaveable(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var expanded by rememberSaveable { mutableStateOf(existing != null) }
    var error by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StudioHeading(if (existing == null) "آشنایی با بیمار" else "اطلاعات پرونده", "نام و کد پرونده برای شروع کافی است.")
            OutlinedTextField(code, { code = it; error = false }, Modifier.fillMaxWidth(), label = { Text("کد پرونده") }, singleLine = true, isError = error && code.isBlank(), shape = RoundedCornerShape(16.dp))
            OutlinedTextField(first, { first = it; error = false }, Modifier.fillMaxWidth(), label = { Text("نام") }, singleLine = true, isError = error && first.isBlank(), shape = RoundedCornerShape(16.dp))
            OutlinedTextField(last, { last = it; error = false }, Modifier.fillMaxWidth(), label = { Text("نام خانوادگی") }, singleLine = true, isError = error && last.isBlank(), shape = RoundedCornerShape(16.dp))
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "بستن اطلاعات تکمیلی" else "اطلاعات تکمیلی · اختیاری") }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(age, { age = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("سن") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    SelectionDropdown("جنسیت", sex, sexOptions, { sex = it }, Modifier.weight(1f))
                }
                OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("تلفن") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("یادداشت") }, minLines = 3)
            }
            if (error) Text("کد پرونده، نام و نام خانوادگی را وارد کنید.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
        StudioPrimary("ذخیرهٔ پرونده", {
            if (code.isBlank() || first.isBlank() || last.isBlank()) error = true
            else onSave(code, first, last, age.toIntOrNull(), sex, phone, notes)
        }, Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun PatientDetailScreen(
    state: PlannerUiState, onNewPlan: () -> Unit, onEditPatient: () -> Unit,
    onPlan: (HairlinePlanVersionEntity) -> Unit, onDeletePatient: () -> Unit,
    onDeletePlan: (HairlinePlanVersionEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val patient = state.selectedPatient
    if (patient == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var deletingPlan by remember { mutableStateOf<HairlinePlanVersionEntity?>(null) }
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("پروندهٔ ${patient.patientCode}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("${patient.firstName} ${patient.lastName}", style = MaterialTheme.typography.headlineSmall)
                        TextButton(onClick = { detailsOpen = true }, enabled = !state.busy, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("اطلاعات و ویرایش پرونده") }
                    }
                }
            }
            item { Text("طرح‌های ذخیره‌شده", style = MaterialTheme.typography.titleMedium) }
            if (state.plans.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StudioIcon(StudioSymbol.Line, Modifier.size(40.dp), MaterialTheme.colorScheme.primary)
                    Text("آمادهٔ اولین طراحی", style = MaterialTheme.typography.titleMedium)
                    Text("یک عکس انتخاب کنید و خط رویش را بسازید.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.plans, key = { it.snapshotId }) { plan ->
                PlanSummaryCard(plan, enabled = !state.busy, onEdit = { onPlan(plan) }, onDelete = { deletingPlan = plan })
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        StudioPrimary("شروع طراحی جدید", onNewPlan, Modifier.padding(bottom = 16.dp), enabled = !state.busy)
    }
    if (detailsOpen) StudioSheet("اطلاعات بیمار", { detailsOpen = false }) {
        Text("${patient.firstName} ${patient.lastName}", style = MaterialTheme.typography.titleMedium)
        val details = listOfNotNull(patient.age?.let { "$it سال" }, patient.sex.takeIf { it != "نامشخص" && it.isNotBlank() }, patient.phone.takeIf(String::isNotBlank))
        if (details.isNotEmpty()) Text(details.joinToString(" · "))
        if (patient.notes.isNotBlank()) Text(patient.notes)
        StudioAction("ویرایش اطلاعات", "نام، شماره تماس و یادداشت‌ها", StudioSymbol.Edit, { detailsOpen = false; onEditPatient() })
        TextButton(onClick = { detailsOpen = false; confirmDelete = true }) { Text("حذف پرونده", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("حذف پرونده؟") },
        text = { Text("همهٔ عکس‌ها و طرح‌های این بیمار نیز حذف می‌شوند. این کار قابل بازگشت نیست.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDeletePatient() }) { Text("حذف پرونده", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } },
    )
    deletingPlan?.let { plan -> AlertDialog(
        onDismissRequest = { deletingPlan = null },
        title = { Text("حذف نسخهٔ ${plan.version} طرح؟") },
        text = { Text("این نسخه و عکس اختصاصی آن حذف می‌شوند. نسخه‌های دیگر و پروندهٔ مشتری باقی می‌مانند. این کار قابل بازگشت نیست.") },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { deletingPlan = null; onDeletePlan(plan) }) { Text("حذف نسخه", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deletingPlan = null }) { Text("انصراف") } },
    ) }
}

@Composable
private fun PlanSummaryCard(plan: HairlinePlanVersionEntity, enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val date = remember(plan.savedAt) { java.text.SimpleDateFormat("d MMM yyyy · HH:mm", java.util.Locale.forLanguageTag("fa")).format(java.util.Date(plan.savedAt)) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("طرح خط رویش · نسخهٔ ${plan.version}", style = MaterialTheme.typography.titleSmall)
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onEdit, enabled = enabled) { StudioIcon(StudioSymbol.Edit); Spacer(Modifier.width(8.dp)); Text("بازکردن و ویرایش") }
                TextButton(onClick = onDelete, enabled = enabled) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun PhotoSourceScreen(onCamera: () -> Unit, onGallery: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StudioSteps(0)
        StudioHeading("از یک تصویر شروع کنیم", "صورت روبه‌رو، پیشانی باز و نور یکنواخت.")
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            val ink = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = "راهنمای عکس روبه‌رو" }) {
                val cx = size.width / 2
                val cy = size.height / 2
                val r = size.height * .32f
                drawOval(ink.copy(alpha = .28f), Offset(cx-r*.72f,cy-r), Size(r*1.44f,r*2), style=Stroke(2.dp.toPx()))
                drawLine(ink.copy(alpha=.25f),Offset(cx,cy-r*1.3f),Offset(cx,cy+r*1.3f),1.dp.toPx(), pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),6.dp.toPx())))
                drawLine(ink,Offset(cx-r*.4f,cy),Offset(cx-r*.15f,cy),2.dp.toPx(),StrokeCap.Round)
                drawLine(ink,Offset(cx+r*.15f,cy),Offset(cx+r*.4f,cy),2.dp.toPx(),StrokeCap.Round)
                val hair = Path().apply { moveTo(cx-r*.65f,cy-r*.45f); cubicTo(cx-r*.5f,cy-r*1.15f,cx+r*.5f,cy-r*1.15f,cx+r*.65f,cy-r*.45f) }
                drawPath(hair,ink,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))
            }
        }
        StudioAction("گرفتن عکس", "با دوربین گوشی", StudioSymbol.Camera, onCamera)
        StudioAction("انتخاب از گالری", "استفاده از عکس موجود", StudioSymbol.Gallery, onGallery)
        Text("بررسی تصویر روی همین دستگاه انجام می‌شود.", Modifier.padding(bottom = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhotoReviewScreen(
    state: PlannerUiState, onAnalyze: () -> Unit, onReference: (HairlineReference) -> Unit,
    onProfile: (HairlineProfile) -> Unit, onCustomRise: (Float) -> Unit,
    onThresholdChange: (Int) -> Unit, onRetake: () -> Unit, modifier: Modifier = Modifier,
) {
    val image = rememberPhotoBitmap(state.pendingPhoto?.originalBytes)
    var advanced by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StudioSteps(0)
            PhotoFrame(image, Modifier.fillMaxWidth().height(240.dp))
            StudioHeading("الگوی شروع را انتخاب کنید", "در مرحلهٔ بعد، خط را با دست اصلاح می‌کنید.")
            HairlinePatternPicker(state.hairlineProfile, onProfile, enabled = !state.busy)
            TextButton(onClick = { advanced = true }, enabled = !state.busy) { StudioIcon(StudioSymbol.Settings); Spacer(Modifier.width(8.dp)); Text("تنظیمات بیشتر") }
            state.rejection?.let { RejectionBanner(it, state.rejectedFaceConfidence, state.configuredThresholdPercent) }
        }
        if (state.busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp)); Text("در حال بررسی چهره…", style = MaterialTheme.typography.bodyMedium)
        }
        StudioPrimary(if (state.busy) "در حال آماده‌سازی…" else "ساخت طرح اولیه", onAnalyze, enabled = !state.busy && state.pendingPhoto != null)
        TextButton(onClick = onRetake, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) { Text("انتخاب عکس دیگر") }
    }
    if (advanced) StudioSheet("تنظیمات طرح اولیه", { advanced = false }) {
        SelectionDropdown("روش ترسیم", if (state.hairlineReference == HairlineReference.FACIAL_PROPORTION) "تناسب چهره" else "دستی", listOf("تناسب چهره", "دستی"), { onReference(if (it == "دستی") HairlineReference.MANUAL else HairlineReference.FACIAL_PROPORTION) }, Modifier.fillMaxWidth())
        StudioToggle("ارتفاع سفارشی", state.hairlineProfile == HairlineProfile.CUSTOM, { onProfile(if (it) HairlineProfile.CUSTOM else HairlineProfile.CURRENT) })
        if (state.hairlineProfile == HairlineProfile.CUSTOM) {
            Text("ارتفاع خط: ${"%.2f".format(state.customRiseRatio)}")
            Slider(state.customRiseRatio, onCustomRise, valueRange = .75f..1.45f)
        }
        FaceConfidenceControl(state.configuredThresholdPercent, state.rejectedFaceConfidence, onThresholdChange)
    }
}

@Composable
private fun HairlinePatternPicker(
    selected: HairlineProfile,
    onSelected: (HairlineProfile) -> Unit,
    enabled: Boolean = true,
) {
    val patterns = listOf(
        HairlineProfile.CURRENT to "متعادل",
        HairlineProfile.CONSERVATIVE to "بالاتر",
        HairlineProfile.LOWER to "پایین‌تر",
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("الگوی خط رویش", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            patterns.forEach { (profile, label) ->
                val active = selected == profile ||
                    (profile == HairlineProfile.CURRENT && (selected == HairlineProfile.MODERATE || selected == HairlineProfile.NATURAL))
                Card(
                    modifier = Modifier.weight(1f).selectable(selected = active, enabled = enabled, role = androidx.compose.ui.semantics.Role.RadioButton) { onSelected(profile) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(
                        width = if (active) 2.dp else 1.dp,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Canvas(Modifier.fillMaxWidth().height(38.dp)) {
                            val path = Path()
                            val y = size.height
                            path.moveTo(5.dp.toPx(), y * 0.62f)
                            val transitionY = when (profile) {
                                HairlineProfile.CONSERVATIVE -> y * 0.67f
                                HairlineProfile.LOWER -> y * 0.82f
                                else -> y * 0.74f
                            }
                            val hookY = y * 0.35f
                            val left = 5.dp.toPx()
                            val right = size.width - left
                            path.cubicTo(size.width * 0.09f, hookY, size.width * 0.16f, transitionY, size.width * 0.28f, transitionY)
                            path.cubicTo(size.width * 0.42f, transitionY, size.width * 0.58f, transitionY, size.width * 0.72f, transitionY)
                            path.cubicTo(size.width * 0.84f, transitionY, size.width * 0.91f, hookY, right, y * 0.62f)
                            drawPath(path, Color(0xFF6E85D8), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceConfidenceControl(value: Int, latestFaceConfidence: Float?, onCommit: (Int) -> Unit) {
    var draftValue by remember(value) { mutableIntStateOf(value) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("آستانهٔ پذیرش چهره", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        latestFaceConfidence?.let { "امتیاز آخرین چهره: ${(it * 100).roundToInt()}٪" }
                            ?: "حداقل اطمینان لازم برای پذیرش عکس",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("$draftValue٪", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
            }
            Slider(
                value = draftValue.toFloat(),
                onValueChange = { draftValue = it.roundToInt().coerceIn(1, 99) },
                onValueChangeFinished = { onCommit(draftValue) },
                valueRange = 1f..99f,
                steps = 97,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("پذیرش آسان‌تر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("سخت‌گیری بیشتر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RejectionBanner(reason: DetectionRejection, faceConfidence: Float?, thresholdPercent: Int) {
    val text = when (reason) {
        DetectionRejection.NO_FACE -> "چهره‌ای تشخیص داده نشد. عکس واضح‌تری انتخاب کنید."
        DetectionRejection.REQUIRED_LANDMARK_MISSING -> "نقاط ضروری چهره کامل پیدا نشدند."
        DetectionRejection.LOW_FACE_CONFIDENCE -> "اطمینان تشخیص کافی نیست. عکس واضح‌تری انتخاب کنید یا تنظیمات را تغییر دهید."
        DetectionRejection.MODEL_ERROR -> "تحلیل تصویر ناموفق بود. تصویر دیگری را امتحان کنید."
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.10f), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("!", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f)) {
                Text("تصویر پذیرفته نشد", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                faceConfidence?.let {
                    Text("بهترین چهره: ${(it * 100).roundToInt()}٪  ·  آستانه: $thresholdPercent٪", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(currentThreshold: Int, onSave: (Int) -> Unit, modifier: Modifier = Modifier) {
    var draft by rememberSaveable(currentThreshold) { mutableIntStateOf(currentThreshold) }
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            StudioHeading("تنظیمات تشخیص", "کیفیت عکس‌های پذیرفته‌شده را تنظیم کنید.")
            FaceConfidenceControl(draft, null, { draft = it })
            Text("مقدار بالاتر یعنی سخت‌گیری بیشتر در پذیرش عکس. مقدار پیش‌فرض ۹۰٪ است.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { draft = 90 }) { Text("بازگرداندن مقدار پیش‌فرض") }
        }
        StudioPrimary("ذخیرهٔ تنظیمات", { onSave(draft) }, Modifier.padding(bottom = 16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(label) { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EditorScreen(state: PlannerUiState, viewModel: PlannerViewModel, onSave: () -> Unit, onLivePlan: () -> Unit, onExportImage: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val image = rememberPhotoBitmap(state.pendingPhoto?.originalBytes)
    var preview by rememberSaveable { mutableStateOf(false) }
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var guides by rememberSaveable { mutableStateOf(false) }
    var panel by remember { mutableStateOf<String?>(null) }
    var appearanceDragRecorded by remember { mutableStateOf(false) }
    val ready = state.analysis != null && image != null && !state.busy
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (preview) "پیش‌نمایش طرح" else "خط رویش را شکل دهید", style = MaterialTheme.typography.titleMedium)
                Text(if (state.saveComplete) "نسخهٔ ${state.savedPlanVersion} ذخیره شد" else if (preview) "طرح را پیش از ذخیره بررسی کنید" else if (state.selectedNode != null) "نقطه انتخاب شد؛ هر جای عکس بکشید" else "یک نقطه را انتخاب کنید؛ سپس هر جای عکس بکشید", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = viewModel::undo, enabled = ready && state.canUndo, modifier = Modifier.semantics { contentDescription = "واگرد" }) { StudioIcon(StudioSymbol.Undo) }
            IconButton(onClick = viewModel::redo, enabled = ready && state.canRedo, modifier = Modifier.semantics { contentDescription = "انجام دوباره" }) { StudioIcon(StudioSymbol.Redo) }
            IconButton(onClick = onLivePlan, enabled = ready && state.saveComplete && state.savedPlanVersion != null, modifier = Modifier.semantics { contentDescription = "نمایش طرح ذخیره‌شده روی دوربین زنده" }) { StudioIcon(StudioSymbol.Camera) }
        }
        if (state.frontalWarning) Text("زاویهٔ صورت کاملاً روبه‌رو نیست؛ تقارن را بررسی کنید.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HairlineCanvas(image, state, viewModel, showControls = !preview && ready, showProposal = !preview || !showOriginal, showGuides = !preview && guides, modifier = Modifier.fillMaxSize())
            if (image == null || state.busy) CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp))
            FilledTonalButton(onClick = { preview = !preview; showOriginal = false }, enabled = ready, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                StudioIcon(if (preview) StudioSymbol.Edit else StudioSymbol.Eye)
                Spacer(Modifier.width(8.dp)); Text(if (preview) "ادامهٔ ویرایش" else "پیش‌نمایش")
            }
        }
        if (preview) {
            StudioToggle("نمایش عکس اصلی", showOriginal, { showOriginal = it })
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorCategory("نقطه‌ها", StudioSymbol.Nodes, Modifier.weight(1f), ready) { panel = "nodes" }
                EditorCategory("ظاهر خط", StudioSymbol.Line, Modifier.weight(1f), ready) { panel = "line" }
                EditorCategory("تراز تصویر", StudioSymbol.Align, Modifier.weight(1f), ready) { panel = "image" }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StudioPrimary(if (state.busy) "در حال ذخیره…" else if (state.saveComplete) "ذخیرهٔ نسخهٔ دیگر" else if (state.currentPlanId != null) "ذخیرهٔ تغییرات" else "ذخیرهٔ طرح", onSave, Modifier.weight(1f), ready)
            OutlinedButton(onClick = { panel = "export" }, enabled = ready, modifier = Modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) { StudioIcon(StudioSymbol.Export); Spacer(Modifier.width(8.dp)); Text("خروجی") }
        }
    }
    when (panel) {
        "nodes" -> StudioSheet("ویرایش نقطه‌ها", { panel = null }) {
            Text("نقطه را روی تصویر انتخاب کنید؛ بعد از هر جای کادر آن را جابه‌جا کنید.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            StudioToggle("تقارن دو طرف", state.symmetryEnabled, { viewModel.toggleSymmetry() })
            val selected = state.nodes.getOrNull(state.selectedNode ?: -1)
            if (selected != null) TextButton(onClick = { viewModel.selectNode(null) }) { Text("لغو انتخاب نقطه") }
            if (selected != null) StudioToggle("دستگیره‌های متقارن", selected.handleMode == BezierHandleMode.MIRRORED, { viewModel.toggleSelectedHandleMode() })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = viewModel::addPoint, enabled = state.nodes.size + (if (state.symmetryEnabled) 2 else 1) <= 12, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(if (state.symmetryEnabled) "افزودن دو نقطه" else "افزودن نقطه") }
                OutlinedButton(onClick = viewModel::removeSelectedPoint, enabled = selected != null && state.nodes.size > (if (state.symmetryEnabled) 5 else 4) && (!state.symmetryEnabled || state.selectedNode != state.nodes.lastIndex - (state.selectedNode ?: 0)), modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("حذف نقطه") }
            }
        }
        "line" -> StudioSheet("ظاهر خط رویش", { panel = null }) {
            Text("ضخامت خط · ${state.hairlineAppearance.strokeWidthDp.roundToInt()}", style = MaterialTheme.typography.titleSmall)
            Slider(state.hairlineAppearance.strokeWidthDp, {
                if (!appearanceDragRecorded) { viewModel.beginEditorChange(); appearanceDragRecorded = true }
                viewModel.updateHairlineAppearance(state.hairlineAppearance.copy(strokeWidthDp = it), recordHistory = false)
            }, onValueChangeFinished = { appearanceDragRecorded = false }, valueRange = 1f..8f, steps = 6)
            StudioToggle("خط‌چین", state.hairlineAppearance.dashed, { viewModel.updateHairlineAppearance(state.hairlineAppearance.copy(dashed = it)) })
            StudioToggle("نمایش خطوط راهنما", guides, { guides = it })
        }
        "image" -> StudioSheet("تراز و نمایش تصویر", { panel = null }) {
            StudioAction("تراز خودکار", "هم‌راستا با محور چهره", StudioSymbol.Align, { viewModel.alignToFace() })
            Text("چرخش تصویر · ${state.transform.rotationDegrees.roundToInt()}°", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { viewModel.changeRotation(-1f) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("۱° به چپ") }
                OutlinedButton(onClick = { viewModel.changeRotation(1f) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("۱° به راست") }
            }
            Text("جابه‌جایی محور تقارن", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.adjustMidline(-.005f) }, modifier = Modifier.weight(1f)) { Text("چپ") }
                TextButton(onClick = viewModel::resetMidline, modifier = Modifier.weight(1f)) { Text("مرکز") }
                OutlinedButton(onClick = { viewModel.adjustMidline(.005f) }, modifier = Modifier.weight(1f)) { Text("راست") }
            }
            StudioAction("بازنشانی بزرگ‌نمایی", "نمایش کامل تصویر", StudioSymbol.Gallery, { viewModel.resetEditorView() })
        }
        "export" -> StudioSheet("دریافت خروجی", { panel = null }) {
            StudioAction("نمایش روی دوربین زنده", if (state.saveComplete) "تطبیق طرح ذخیره‌شده با چهرهٔ مشتری" else "ابتدا تغییرات طرح را ذخیره کنید", StudioSymbol.Camera, { panel = null; onLivePlan() }, enabled = ready && state.saveComplete && state.savedPlanVersion != null)
            StudioAction("تصویر طرح", "عکس و خط رویش، بدون ابزارهای ویرایش", StudioSymbol.Gallery, { panel = null; onExportImage(false) })
            StudioAction("تصویر با راهنماها", "همراه با محور تقارن و نقطه‌های طرح", StudioSymbol.Nodes, { panel = null; onExportImage(true) })
        }
    }
}

@Composable
private fun EditorCategory(label: String, symbol: StudioSymbol, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StudioIcon(symbol, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HairlineCanvas(image: ImageBitmap?, state: PlannerUiState, viewModel: PlannerViewModel, showControls: Boolean, showProposal: Boolean = true, showGuides: Boolean = true, modifier: Modifier = Modifier) {
    val latestImage by rememberUpdatedState(image)
    val interaction = if (showControls) Modifier.pointerInput(Unit) {
        var lastTapTime: Long? = null
        var lastTapPosition = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown()
            val frame = latestImage ?: return@awaitEachGesture
            val current = viewModel.state.value
            val bounds = fitRect(size.width.toFloat(), size.height.toFloat(), frame.width, frame.height)
            // Resolve the target at finger-down, before touch slop changes its position.
            val nearest = current.nodes.flatMapIndexed { index, node ->
                buildList {
                    add(DragTarget(index, null) to Offset(node.x, node.y))
                    if (index == current.selectedNode) {
                        if (index > 0) add(DragTarget(index, false) to Offset(node.inX, node.inY))
                        if (index < current.nodes.lastIndex) add(DragTarget(index, true) to Offset(node.outX, node.outY))
                    }
                }.map { (target, point) -> target to mappedPoint(point, bounds, size.width.toFloat(), size.height.toFloat(), current.transform) }
            }.minByOrNull { (_, point) -> (down.position - point).getDistance() }
            val directTarget = nearest?.takeIf { (down.position - it.second).getDistance() < 36.dp.toPx() }?.first
            val target = directTarget ?: current.selectedNode?.takeIf { it in current.nodes.indices }?.let { DragTarget(it, null) }
            var previousDragPosition = down.position
            var dragging = false
            var multiTouch = false
            var historyRecorded = false
            var cancelled = false
            down.consume()

            do {
                val event = awaitPointerEvent()
                if (event.changes.any { it.isConsumed }) cancelled = true
                // Once a second finger joins, do not resume node dragging until all fingers lift.
                if (event.changes.count { it.pressed || it.previousPressed } > 1) {
                    multiTouch = true
                    lastTapTime = null
                }
                if (!cancelled && multiTouch) {
                    if (event.changes.count { it.pressed } >= 2) {
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom != 1f || pan != Offset.Zero) {
                            if (!historyRecorded) { viewModel.beginEditorChange(); historyRecorded = true }
                            viewModel.updateGestureZoomPan(zoom, pan.x / bounds.width, pan.y / bounds.height)
                        }
                    }
                    event.changes.forEach { it.consume() }
                } else if (!cancelled) {
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null) {
                        if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                            lastTapTime = null
                            if (target != null) {
                                viewModel.beginEditorChange()
                                historyRecorded = true
                                viewModel.selectNode(target.index)
                            }
                        }
                        if (dragging) {
                            val node = target?.let { viewModel.state.value.nodes.getOrNull(it.index) }
                            if (target != null && node != null) {
                                val anchor = when (target.outgoing) {
                                    null -> FacePoint(node.x, node.y)
                                    false -> FacePoint(node.inX, node.inY)
                                    true -> FacePoint(node.outX, node.outY)
                                }
                                val delta = change.position - previousDragPosition
                                val moved = CoordinateTransformer.translateByCanvasDelta(anchor, FacePoint(delta.x, delta.y), bounds, viewModel.state.value.transform)
                                when (target.outgoing) {
                                    null -> viewModel.moveNode(target.index, moved.x, moved.y)
                                    else -> viewModel.moveHandle(target.index, target.outgoing, moved.x, moved.y)
                                }
                            }
                            previousDragPosition = change.position
                            change.consume()
                        } else if (!change.pressed) {
                            val elapsed = lastTapTime?.let { down.uptimeMillis - it }
                            val doubleTap = elapsed != null && elapsed in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
                                (down.position - lastTapPosition).getDistance() < 40.dp.toPx()
                            if (doubleTap) {
                                viewModel.resetEditorView()
                                lastTapTime = null
                            } else {
                                // Empty taps retain selection, ready for the next remote drag.
                                directTarget?.let { viewModel.selectNode(it.index) }
                                lastTapTime = change.uptimeMillis
                                lastTapPosition = down.position
                            }
                            change.consume()
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
            if (cancelled) lastTapTime = null
        }
    } else Modifier

    Canvas(modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF24212C)).then(interaction)) {
        if (image == null) {
            drawRect(Color.DarkGray)
            return@Canvas
        }
        val bounds = fitRect(size.width, size.height, image.width, image.height)
        val pivot = Offset(size.width / 2f, size.height / 2f)
        rotate(state.transform.rotationDegrees, pivot) {
            scale(state.transform.scale, state.transform.scale, pivot) {
                translate(state.transform.translationX * bounds.width, state.transform.translationY * bounds.height) {
                    drawImage(image, dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()), dstSize = IntSize(bounds.width.roundToInt(), bounds.height.roundToInt()), filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
                }
            }
        }
        val mappedNodes = state.nodes.map { node ->
            listOf(
                mappedPoint(Offset(node.x, node.y), bounds, size.width, size.height, state.transform),
                mappedPoint(Offset(node.inX, node.inY), bounds, size.width, size.height, state.transform),
                mappedPoint(Offset(node.outX, node.outY), bounds, size.width, size.height, state.transform),
            )
        }
        val curve = Path()
        if (showProposal && mappedNodes.isNotEmpty()) {
            curve.moveTo(mappedNodes.first()[0].x, mappedNodes.first()[0].y)
            mappedNodes.zipWithNext().forEach { (from, to) -> curve.cubicTo(from[2].x, from[2].y, to[1].x, to[1].y, to[0].x, to[0].y) }
            val dash = if (state.hairlineAppearance.dashed) {
                androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
            } else null
            val shadowOffsetX = 0.8.dp.toPx()
            val shadowOffsetY = 1.1.dp.toPx()
            withTransform({ translate(shadowOffsetX, shadowOffsetY) }) {
                drawPath(
                    curve,
                    Color.Black.copy(alpha = 0.32f),
                    style = Stroke(width = (state.hairlineAppearance.strokeWidthDp + 2.2f).dp.toPx(), cap = StrokeCap.Round),
                )
            }
            drawPath(
                curve,
                Color(0xFF2F8FEA),
                style = Stroke(width = state.hairlineAppearance.strokeWidthDp.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash),
            )
        }
        if (showControls && showGuides) {
            state.analysis?.landmarks?.forEach { landmark ->
                val point = mappedPoint(Offset(landmark.x, landmark.y), bounds, size.width, size.height, state.transform)
                when (landmark.name) {
                    "LEFT_PUPIL", "RIGHT_PUPIL" -> drawCircle(Color(0xFFD9EEFF).copy(alpha = 0.52f), radius = 1.7.dp.toPx(), center = point)
                    "LEFT_IRIS_BOTTOM", "RIGHT_IRIS_BOTTOM" -> drawCircle(Color(0xFF8CC8FF).copy(alpha = 0.72f), radius = 2.2.dp.toPx(), center = point)
                    else -> drawCircle(Color(0xFF9FD3FF).copy(alpha = 0.62f), radius = 2.4.dp.toPx(), center = point)
                }
            }
            val landmarks = state.analysis?.landmarks.orEmpty()
            HairlineGeometry.lowerIrisLine(landmarks)?.let { eyeAxis ->
                val guide = HairlineGeometry.lineAcrossImage(eyeAxis)
                drawLine(
                    Color(0xFF63B4FF).copy(alpha = 0.9f),
                    mappedPoint(Offset(guide.start.x, guide.start.y), bounds, size.width, size.height, state.transform),
                    mappedPoint(Offset(guide.end.x, guide.end.y), bounds, size.width, size.height, state.transform),
                    1.2.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
                )
            }
            HairlineGeometry.facialAxis(landmarks, image.width.toFloat() / image.height.coerceAtLeast(1))?.let { rawAxis ->
                val guide = HairlineGeometry.lineAcrossImage(HairlineGeometry.shiftedAxis(rawAxis, state.transform.midlineOffset))
                val midTop = mappedPoint(Offset(guide.start.x, guide.start.y), bounds, size.width, size.height, state.transform)
                val midBottom = mappedPoint(Offset(guide.end.x, guide.end.y), bounds, size.width, size.height, state.transform)
                drawLine(Color(0xFF63B4FF).copy(alpha = 0.9f), midTop, midBottom, 1.2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())))
            }
            listOf("LEFT_TEMPLE", "RIGHT_TEMPLE").forEach { name ->
                state.analysis?.landmarks?.firstOrNull { it.name == name }?.let { temple ->
                    drawLine(
                        Color(0xFF8CC8FF).copy(alpha = 0.48f),
                        mappedPoint(Offset(temple.x, 0f), bounds, size.width, size.height, state.transform),
                        mappedPoint(Offset(temple.x, 1f), bounds, size.width, size.height, state.transform),
                        0.9.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 7.dp.toPx())),
                    )
                }
            }
        }
        if (showControls) {
            mappedNodes.forEachIndexed { index, points ->
                val selected = index == state.selectedNode
                if (!selected) return@forEachIndexed
                val handleColor = Color.White.copy(alpha = if (selected) 0.86f else 0.42f)
                if (index > 0) {
                    drawLine(handleColor, points[1], points[0], 1.5.dp.toPx())
                    drawCircle(handleColor, radius = 4.dp.toPx(), center = points[1])
                }
                if (index < mappedNodes.lastIndex) {
                    drawLine(handleColor, points[0], points[2], 1.5.dp.toPx())
                    drawCircle(handleColor, radius = 4.dp.toPx(), center = points[2])
                }
            }
        }
        if (showControls) mappedNodes.forEachIndexed { index, points ->
            drawCircle(if (index == state.selectedNode) Color(0xFFFFA34D) else Color.White, radius = if (index == state.selectedNode) 7.dp.toPx() else 5.dp.toPx(), center = points[0])
        }
    }
}

private data class DragTarget(val index: Int, val outgoing: Boolean?)
private typealias FrameRect = ImageViewport

private fun fitRect(canvasWidth: Float, canvasHeight: Float, imageWidth: Int, imageHeight: Int): FrameRect {
    return CoordinateTransformer.fitViewport(canvasWidth, canvasHeight, imageWidth, imageHeight)
}

private fun mappedPoint(point: Offset, rect: FrameRect, canvasWidth: Float, canvasHeight: Float, transform: EditorTransform): Offset {
    val pointOnCanvas = CoordinateTransformer.toCanvas(FacePoint(point.x, point.y), rect, canvasWidth / 2f, canvasHeight / 2f, transform)
    return Offset(pointOnCanvas.x, pointOnCanvas.y)
}

@Composable
private fun PhotoFrame(image: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF24212C)), contentAlignment = Alignment.Center) {
        if (image == null) Text("تصویری انتخاب نشده است.", color = Color.White)
        else androidx.compose.foundation.Image(image, contentDescription = "عکس بیمار", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun rememberPhotoBitmap(bytes: ByteArray?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = bytes) {
        value = withContext(Dispatchers.IO) { bytes?.let { ImageDecoder.decode(it)?.bitmap?.asImageBitmap() } }
    }
    return image
}

@Composable
private fun CameraCaptureScreen(onCaptured: (ByteArray) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember(context) { PreviewView(context).apply {
        // Keep preview composition in the view hierarchy with the Compose overlays.
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        keepScreenOn = true
    } }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    val scope = rememberCoroutineScope()
    DisposableEffect(context, lifecycleOwner) {
        var active = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (active) try {
                provider = future.get()
            } catch (error: Exception) { cameraError = error.localizedMessage ?: "دوربین در دسترس نیست." }
        }, mainExecutor)
        onDispose { active = false }
    }
    DisposableEffect(provider, lensFacing, previewView, lifecycleOwner) {
        val cameraProvider = provider
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        capture = null
        cameraError = null
        if (cameraProvider != null) try {
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            if (!cameraProvider.hasCamera(selector)) {
                cameraError = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "دوربین جلو در دسترس نیست." else "دوربین پشت در دسترس نیست."
            } else {
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                capture = imageCapture
            }
        } catch (error: Exception) {
            cameraError = error.localizedMessage ?: "دوربین در دسترس نیست."
        }
        // Release only this session, never another screen's newly bound camera.
        onDispose { cameraProvider?.unbind(preview, imageCapture) }
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val ovalWidth = size.width * 0.68f
            val ovalHeight = size.height * 0.40f
            val ovalLeft = (size.width - ovalWidth) / 2f
            val ovalTop = size.height * 0.18f
            drawOval(
                color = Color.White.copy(alpha = 0.76f),
                topLeft = Offset(ovalLeft, ovalTop),
                size = Size(ovalWidth, ovalHeight),
                style = Stroke(width = 2.dp.toPx()),
            )
            val eyeLine = ovalTop + ovalHeight * 0.43f
            drawLine(
                color = Color.White.copy(alpha = 0.38f),
                start = Offset(ovalLeft + ovalWidth * 0.12f, eyeLine),
                end = Offset(ovalLeft + ovalWidth * 0.88f, eyeLine),
                strokeWidth = 1.dp.toPx(),
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color(0xEE24212C)).padding(horizontal = 18.dp, vertical = 17.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text("صورت را روبه‌روی دوربین و در مرکز کادر قرار دهید.", color = Color.White)
            cameraError?.let { Text(it, color = Color(0xFFFFB4AB)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !busy, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), modifier = Modifier.weight(1f)) { Text("بازگشت") }
                OutlinedButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                ) { Text(if (lensFacing == CameraSelector.LENS_FACING_BACK) "دوربین جلو" else "دوربین پشت") }
            }
            Button(onClick = {
                    val imageCapture = capture ?: return@Button
                    busy = true
                    val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                    val options = ImageCapture.OutputFileOptions.Builder(file).build()
                    imageCapture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            scope.launch {
                                val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull().also { file.delete() } }
                                busy = false
                                if (bytes != null) onCaptured(bytes) else cameraError = "ذخیرهٔ موقت عکس انجام نشد."
                            }
                        }
                        override fun onError(exception: ImageCaptureException) { busy = false; cameraError = exception.localizedMessage ?: "عکس‌برداری انجام نشد." }
                    })
                }, enabled = capture != null && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { StudioIcon(StudioSymbol.Camera); Spacer(Modifier.width(10.dp)); Text(if (busy) "در حال ثبت…" else "ثبت عکس") }
        }
        if (capture == null && cameraError == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
    }
}
