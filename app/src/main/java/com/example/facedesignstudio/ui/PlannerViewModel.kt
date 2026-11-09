package com.example.facedesignstudio.ui

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.facedesignstudio.data.PlannerRepository
import com.example.facedesignstudio.data.local.AppSettingsEntity
import com.example.facedesignstudio.data.local.HairlinePlanVersionEntity
import com.example.facedesignstudio.data.local.PatientEntity
import com.example.facedesignstudio.domain.BezierNode
import com.example.facedesignstudio.domain.BezierHandleMode
import com.example.facedesignstudio.domain.DetectionAnalysis
import com.example.facedesignstudio.domain.DetectionOutcome
import com.example.facedesignstudio.domain.DetectionRejection
import com.example.facedesignstudio.domain.EditorTransform
import com.example.facedesignstudio.domain.FaceAxis
import com.example.facedesignstudio.domain.FacePoint
import com.example.facedesignstudio.domain.FaceDetectionEngine
import com.example.facedesignstudio.domain.HairlineAppearance
import com.example.facedesignstudio.domain.FacialLandmark
import com.example.facedesignstudio.domain.HairlineGeometry
import com.example.facedesignstudio.domain.HairlineProfile
import com.example.facedesignstudio.domain.HairlineReference
import com.example.facedesignstudio.domain.ImageDecoder
import com.example.facedesignstudio.domain.approximateFrontalityMessageScore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayInputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PendingPhoto(
    val originalBytes: ByteArray,
    val source: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val exifRotation: Int,
)

data class PlannerUiState(
    val patients: List<PatientEntity> = emptyList(),
    val settings: AppSettingsEntity = AppSettingsEntity(minimumFaceConfidencePercent = PlannerRepository.DEFAULT_THRESHOLD_PERCENT),
    val selectedPatient: PatientEntity? = null,
    val plans: List<HairlinePlanVersionEntity> = emptyList(),
    val pendingPhoto: PendingPhoto? = null,
    val analysis: DetectionAnalysis? = null,
    val rejection: DetectionRejection? = null,
    val rejectedFaceConfidence: Float? = null,
    val busy: Boolean = false,
    val saveComplete: Boolean = false,
    val savedPlanVersion: Int? = null,
    val nodes: List<BezierNode> = emptyList(),
    val transform: EditorTransform = EditorTransform(),
    val hairlineAppearance: HairlineAppearance = HairlineAppearance(),
    val selectedNode: Int? = null,
    val symmetryEnabled: Boolean = true,
    val hairlineReference: HairlineReference = HairlineReference.FACIAL_PROPORTION,
    val hairlineProfile: HairlineProfile = HairlineProfile.CURRENT,
    val customRiseRatio: Float = 1.05f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val currentPlanId: String? = null,
    val activePlanThresholdPercent: Int? = null,
    val messageKey: String? = null,
    val patientCodeSearch: String = "",
) {
    val configuredThresholdPercent: Int get() = settings.minimumFaceConfidencePercent ?: PlannerRepository.DEFAULT_THRESHOLD_PERCENT
    val thresholdPercent: Int get() = activePlanThresholdPercent ?: configuredThresholdPercent
    val frontalWarning: Boolean get() = analysis?.let { approximateFrontalityMessageScore(it.frontalScore) } ?: false
}

private data class EditorSnapshot(
    val nodes: List<BezierNode>,
    val transform: EditorTransform,
    val hairlineAppearance: HairlineAppearance,
    val symmetryEnabled: Boolean,
)

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val repository: PlannerRepository,
    private val detectionEngine: FaceDetectionEngine,
) : ViewModel() {
    private val _state = MutableStateFlow(PlannerUiState())
    val state: StateFlow<PlannerUiState> = _state.asStateFlow()
    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private var plansJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observePatients().collect { patients -> _state.update { it.copy(patients = patients) } }
        }
        viewModelScope.launch {
            repository.ensureDefaultThreshold()
            repository.observeSettings().collect { settings ->
                _state.update {
                    it.copy(settings = settings ?: AppSettingsEntity(minimumFaceConfidencePercent = PlannerRepository.DEFAULT_THRESHOLD_PERCENT))
                }
            }
        }
    }

    fun setSearchQuery(value: String) = _state.update { it.copy(patientCodeSearch = value) }

    fun saveThreshold(percent: Int) {
        val safePercent = percent.coerceIn(1, 99)
        _state.update { current ->
            current.copy(
                settings = AppSettingsEntity(minimumFaceConfidencePercent = safePercent),
                messageKey = null,
            )
        }
        viewModelScope.launch {
            repository.saveThreshold(safePercent)
            _state.update { it.copy(messageKey = "settings_saved") }
        }
    }

    fun setPlanningOptions(
        reference: HairlineReference = _state.value.hairlineReference,
        profile: HairlineProfile = _state.value.hairlineProfile,
        customRiseRatio: Float = _state.value.customRiseRatio,
        naturalness: Float = _state.value.hairlineAppearance.naturalness,
    ) {
        val previous = _state.value
        val safeNaturalness = naturalness.coerceIn(0f, 1f)
        val changed = previous.hairlineReference != reference || previous.hairlineProfile != profile || previous.customRiseRatio != customRiseRatio || previous.hairlineAppearance.naturalness != safeNaturalness
        _state.update {
            it.copy(
                hairlineReference = reference,
                hairlineProfile = profile,
                customRiseRatio = customRiseRatio.coerceIn(0.75f, 1.45f),
                hairlineAppearance = it.hairlineAppearance.copy(naturalness = safeNaturalness),
                analysis = if (changed) null else it.analysis,
                rejection = if (changed) null else it.rejection,
                rejectedFaceConfidence = if (changed) null else it.rejectedFaceConfidence,
                nodes = if (changed) emptyList() else it.nodes,
                saveComplete = if (changed) false else it.saveComplete,
            )
        }
        if (changed) clearHistory()
    }

    fun selectPatient(patientId: String) {
        plansJob?.cancel()
        _state.update { it.copy(selectedPatient = null, plans = emptyList()) }
        viewModelScope.launch {
            val patient = repository.getPatient(patientId)
            _state.update { it.copy(selectedPatient = patient, plans = emptyList()) }
            plansJob = viewModelScope.launch {
                repository.observePlans(patientId).collect { plans -> _state.update { it.copy(plans = plans) } }
            }
        }
    }

    fun savePatient(
        id: String? = null,
        patientCode: String,
        firstName: String,
        lastName: String,
        age: Int?,
        sex: String,
        phone: String,
        notes: String,
        onSaved: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entity = PatientEntity(
                id = id ?: UUID.randomUUID().toString(),
                patientCode = patientCode.trim(),
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                age = age,
                sex = sex,
                phone = phone.trim(),
                notes = notes.trim(),
                createdAt = id?.let { currentId -> repository.getPatient(currentId)?.createdAt } ?: now,
                updatedAt = now,
            )
            runCatching { repository.savePatient(entity) }
                .onSuccess {
                    _state.update { it.copy(messageKey = "patient_saved") }
                    onSaved(entity.id)
                }
                .onFailure { _state.update { it.copy(messageKey = "patient_code_exists") } }
        }
    }

    fun deletePatient(patientId: String) {
        viewModelScope.launch {
            repository.deletePatient(patientId)
            _state.update { it.copy(selectedPatient = null, plans = emptyList(), messageKey = "patient_deleted") }
        }
    }

    fun selectPhoto(bytes: ByteArray, source: String, mimeType: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            val rotation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).rotationDegrees }.getOrDefault(0)
            val width = if (rotation == 90 || rotation == 270) options.outHeight else options.outWidth
            val height = if (rotation == 90 || rotation == 270) options.outWidth else options.outHeight
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        pendingPhoto = PendingPhoto(bytes, source, mimeType ?: "image/jpeg", width, height, rotation),
                        analysis = null,
                        rejection = null,
                        rejectedFaceConfidence = null,
                        nodes = emptyList(),
                        transform = EditorTransform(),
                        selectedNode = null,
                        saveComplete = false,
                        savedPlanVersion = null,
                        currentPlanId = null,
                        activePlanThresholdPercent = null,
                        messageKey = null,
                    )
                }
                clearHistory()
            }
        }
    }

    fun analyzePhoto() {
        val snapshot = _state.value
        val pending = snapshot.pendingPhoto ?: return
        val threshold = snapshot.thresholdPercent
        viewModelScope.launch {
            _state.update { it.copy(busy = true, rejection = null, rejectedFaceConfidence = null, analysis = null, messageKey = null) }
            val decoded = withContext(Dispatchers.Default) { ImageDecoder.decode(pending.originalBytes) }
            if (decoded == null) {
                _state.update { it.copy(busy = false, rejection = DetectionRejection.MODEL_ERROR, rejectedFaceConfidence = null) }
                return@launch
            }
            val outcome = withContext(Dispatchers.Default) {
                try {
                    detectionEngine.analyze(decoded.bitmap, threshold / 100f)
                } finally {
                    decoded.bitmap.recycle()
                }
            }
            when (outcome) {
                is DetectionOutcome.Rejected -> _state.update {
                    it.copy(busy = false, rejection = outcome.reason, rejectedFaceConfidence = outcome.faceConfidence, analysis = null, nodes = emptyList())
                }
                is DetectionOutcome.Accepted -> {
                    val proposal = HairlineGeometry.initialProposal(
                        outcome.analysis,
                        snapshot.hairlineReference,
                        snapshot.hairlineProfile,
                        snapshot.customRiseRatio,
                        snapshot.hairlineAppearance.naturalness,
                        pending.width.toFloat() / pending.height.coerceAtLeast(1),
                    )
                    _state.update {
                        val proposalNodes = HairlineGeometry.setSymmetric(
                            proposal.nodes,
                            snapshot.symmetryEnabled,
                            HairlineGeometry.facialAxis(
                                outcome.analysis.landmarks,
                                pending.width.toFloat() / pending.height.coerceAtLeast(1),
                            ),
                            pending.width.toFloat() / pending.height.coerceAtLeast(1),
                        )
                        it.copy(
                            busy = false,
                            activePlanThresholdPercent = threshold,
                            analysis = outcome.analysis,
                            rejection = null,
                            rejectedFaceConfidence = null,
                            nodes = proposalNodes,
                            transform = proposal.transform,
                            selectedNode = null,
                        )
                    }
                    clearHistory()
                }
            }
        }
    }

    fun beginEditorChange() {
        val current = _state.value
        if (current.nodes.isEmpty()) return
        _state.update { it.copy(saveComplete = false) }
        undoStack.addLast(EditorSnapshot(current.nodes, current.transform, current.hairlineAppearance, current.symmetryEnabled))
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        updateHistoryFlags()
    }

    fun moveNode(index: Int, x: Float, y: Float) {
        _state.update { state ->
            if (index !in state.nodes.indices) return@update state
            val nodes = state.nodes.toMutableList()
            val old = nodes[index]
            val deltaX = x.coerceIn(0f, 1f) - old.x
            val deltaY = y.coerceIn(0f, 1f) - old.y
            val moved = old.copy(
                x = x.coerceIn(0f, 1f),
                y = y.coerceIn(0f, 1f),
                inX = (old.inX + deltaX).coerceIn(0f, 1f),
                inY = (old.inY + deltaY).coerceIn(0f, 1f),
                outX = (old.outX + deltaX).coerceIn(0f, 1f),
                outY = (old.outY + deltaY).coerceIn(0f, 1f),
            )
            val axis = editorAxis(state)
            if (state.symmetryEnabled && axis != null) {
                val oppositeIndex = nodes.lastIndex - index
                if (oppositeIndex == index) {
                    val projected = HairlineGeometry.projectOntoAxis(FacePoint(moved.x, moved.y), axis, imageAspectRatio(state))
                    val outgoing = HairlineGeometry.reflectPoint(FacePoint(moved.inX, moved.inY), axis, imageAspectRatio(state))
                    nodes[index] = moved.copy(x = projected.x, y = projected.y, outX = outgoing.x, outY = outgoing.y)
                } else {
                    nodes[index] = moved
                    nodes[oppositeIndex] = HairlineGeometry.mirrorNode(moved, axis, imageAspectRatio(state))
                }
            } else nodes[index] = moved
            state.copy(nodes = nodes, selectedNode = index)
        }
    }

    fun moveHandle(index: Int, outgoing: Boolean, x: Float, y: Float) {
        _state.update { state ->
            if (index !in state.nodes.indices) return@update state
            val node = state.nodes[index]
            val newX = x.coerceIn(0f, 1f)
            val newY = y.coerceIn(0f, 1f)
            val updated = if (outgoing) {
                node.copy(
                    outX = newX,
                    outY = newY,
                    inX = if (node.handleMode == BezierHandleMode.MIRRORED) (2f * node.x - newX).coerceIn(0f, 1f) else node.inX,
                    inY = if (node.handleMode == BezierHandleMode.MIRRORED) (2f * node.y - newY).coerceIn(0f, 1f) else node.inY,
                )
            } else {
                node.copy(
                    inX = newX,
                    inY = newY,
                    outX = if (node.handleMode == BezierHandleMode.MIRRORED) (2f * node.x - newX).coerceIn(0f, 1f) else node.outX,
                    outY = if (node.handleMode == BezierHandleMode.MIRRORED) (2f * node.y - newY).coerceIn(0f, 1f) else node.outY,
                )
            }
            val nodes = state.nodes.toMutableList()
            val axis = editorAxis(state)
            if (state.symmetryEnabled && axis != null) {
                val oppositeIndex = nodes.lastIndex - index
                if (oppositeIndex == index) {
                    val incoming = FacePoint(updated.inX, updated.inY)
                    val outgoingPoint = FacePoint(updated.outX, updated.outY)
                    val reflected = HairlineGeometry.reflectPoint(if (outgoing) outgoingPoint else incoming, axis, imageAspectRatio(state))
                    nodes[index] = if (outgoing) updated.copy(inX = reflected.x, inY = reflected.y)
                    else updated.copy(outX = reflected.x, outY = reflected.y)
                } else {
                    nodes[index] = updated
                    nodes[oppositeIndex] = HairlineGeometry.mirrorNode(updated, axis, imageAspectRatio(state))
                }
            } else nodes[index] = updated
            state.copy(nodes = nodes, selectedNode = index)
        }
    }

    fun selectNode(index: Int?) = _state.update { it.copy(selectedNode = index) }

    fun toggleSymmetry() {
        beginEditorChange()
        _state.update { state ->
            val enabled = !state.symmetryEnabled
            state.copy(
                symmetryEnabled = enabled,
                nodes = HairlineGeometry.setSymmetric(state.nodes, enabled, editorAxis(state), imageAspectRatio(state)),
            )
        }
    }

    fun toggleSelectedHandleMode() {
        val selected = _state.value.selectedNode ?: return
        beginEditorChange()
        _state.update { state ->
            if (selected !in state.nodes.indices) state else state.copy(
                nodes = state.nodes.toMutableList().also { nodes ->
                    val mode = if (nodes[selected].handleMode == BezierHandleMode.MIRRORED) BezierHandleMode.INDEPENDENT else BezierHandleMode.MIRRORED
                    nodes[selected] = nodes[selected].copy(handleMode = mode)
                    if (state.symmetryEnabled && selected != nodes.lastIndex - selected) {
                        val axis = editorAxis(state)
                        if (axis != null) nodes[nodes.lastIndex - selected] = HairlineGeometry.mirrorNode(nodes[selected], axis, imageAspectRatio(state))
                    }
                },
            )
        }
    }

    fun addPoint() {
        val state = _state.value
        val symmetric = state.symmetryEnabled && editorAxis(state) != null
        if (state.nodes.size + (if (symmetric) 2 else 1) > 12) return
        val insertion = HairlineGeometry.insertNodes(state.nodes, state.selectedNode, symmetric) ?: return
        beginEditorChange()
        _state.update { it.copy(nodes = insertion.nodes, selectedNode = insertion.selectedIndex) }
    }

    fun removeSelectedPoint() {
        val current = _state.value
        val index = current.selectedNode ?: return
        if (index !in current.nodes.indices) return
        val axis = editorAxis(current)
        if (current.symmetryEnabled && axis != null) {
            if (current.nodes.size <= 5 || index == current.nodes.lastIndex - index) return
        } else if (current.nodes.size <= 4) return
        beginEditorChange()
        _state.update { state ->
            val nodes = state.nodes.toMutableList()
            if (state.symmetryEnabled && axis != null) {
                val oppositeIndex = nodes.lastIndex - index
                nodes.removeAt(maxOf(index, oppositeIndex))
                nodes.removeAt(minOf(index, oppositeIndex))
            } else nodes.removeAt(index)
            state.copy(nodes = nodes, selectedNode = null)
        }
    }

    fun changeRotation(delta: Float) {
        beginEditorChange()
        _state.update { it.copy(transform = it.transform.copy(rotationDegrees = (it.transform.rotationDegrees + delta).coerceIn(-90f, 90f))) }
    }

    fun alignToFace() {
        val state = _state.value
        val analysis = state.analysis ?: return
        beginEditorChange()
        val angle = HairlineGeometry.alignmentRotationDegrees(analysis.landmarks, imageAspectRatio(state))
        _state.update { it.copy(transform = it.transform.copy(rotationDegrees = angle)) }
    }

    fun updateHairlineAppearance(appearance: HairlineAppearance, recordHistory: Boolean = true) {
        if (recordHistory) beginEditorChange()
        _state.update { it.copy(hairlineAppearance = appearance.copy(strokeWidthDp = appearance.strokeWidthDp.coerceIn(1f, 8f))) }
    }

    fun adjustMidline(delta: Float) {
        beginEditorChange()
        _state.update { state ->
            val transform = state.transform.copy(midlineOffset = (state.transform.midlineOffset + delta).coerceIn(-0.2f, 0.2f))
            val next = state.copy(transform = transform)
            next.copy(nodes = HairlineGeometry.setSymmetric(state.nodes, state.symmetryEnabled, editorAxis(next), imageAspectRatio(state)))
        }
    }

    fun resetMidline() {
        beginEditorChange()
        _state.update { state ->
            val next = state.copy(transform = state.transform.copy(midlineOffset = 0f))
            next.copy(nodes = HairlineGeometry.setSymmetric(state.nodes, state.symmetryEnabled, editorAxis(next), imageAspectRatio(state)))
        }
    }

    fun resetEditorView() {
        beginEditorChange()
        _state.update { it.copy(transform = it.transform.copy(scale = 1f, translationX = 0f, translationY = 0f)) }
    }

    fun updateGestureZoomPan(zoomFactor: Float, panX: Float, panY: Float) = _state.update { state ->
        state.copy(transform = state.transform.copy(
            scale = (state.transform.scale * zoomFactor).coerceIn(1f, 4f),
            translationX = (state.transform.translationX + panX).coerceIn(-1f, 1f),
            translationY = (state.transform.translationY + panY).coerceIn(-1f, 1f),
        ))
    }

    fun panEditor(dx: Float, dy: Float) = _state.update {
        it.copy(transform = it.transform.copy(translationX = (it.transform.translationX + dx).coerceIn(-1f, 1f), translationY = (it.transform.translationY + dy).coerceIn(-1f, 1f)))
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _state.value
        redoStack.addLast(EditorSnapshot(current.nodes, current.transform, current.hairlineAppearance, current.symmetryEnabled))
        val prior = undoStack.removeLast()
        _state.update { it.copy(nodes = prior.nodes, transform = prior.transform, hairlineAppearance = prior.hairlineAppearance, symmetryEnabled = prior.symmetryEnabled, selectedNode = null, saveComplete = false) }
        updateHistoryFlags()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _state.value
        undoStack.addLast(EditorSnapshot(current.nodes, current.transform, current.hairlineAppearance, current.symmetryEnabled))
        val next = redoStack.removeLast()
        _state.update { it.copy(nodes = next.nodes, transform = next.transform, hairlineAppearance = next.hairlineAppearance, symmetryEnabled = next.symmetryEnabled, selectedNode = null, saveComplete = false) }
        updateHistoryFlags()
    }

    fun savePlan() {
        val current = _state.value
        val patient = current.selectedPatient ?: return
        val pending = current.pendingPhoto ?: return
        val analysis = current.analysis ?: return
        val threshold = current.thresholdPercent
        viewModelScope.launch {
            _state.update { it.copy(busy = true, saveComplete = false, messageKey = null) }
            val result = runCatching {
                repository.savePhotoAndPlan(
                    patientId = patient.id,
                    source = pending.source,
                    mimeType = pending.mimeType,
                    width = pending.width,
                    height = pending.height,
                    exifRotation = pending.exifRotation,
                    imageBytes = pending.originalBytes,
                    faceConfidence = analysis.faceConfidence,
                    thresholdPercent = threshold,
                    frontalScore = analysis.frontalScore,
                    landmarksJson = HairlineGeometry.landmarksToJson(analysis.landmarks),
                    nodesJson = HairlineGeometry.nodesToJson(current.nodes),
                    transformJson = HairlineGeometry.transformToJson(current.transform, current.hairlineAppearance),
                    modelVersion = FaceDetectionEngine.MODEL_VERSION,
                    algorithmVersion = HairlineGeometry.ALGORITHM_VERSION,
                    referenceMethod = current.hairlineReference.name,
                    hairlineProfile = current.hairlineProfile.name,
                    balancedHandles = false,
                    symmetryEnabled = current.symmetryEnabled,
                    customRiseRatio = current.customRiseRatio,
                    planId = current.currentPlanId,
                )
            }
            result.onSuccess { saved ->
                _state.update {
                    it.copy(busy = false, saveComplete = true, savedPlanVersion = saved.version, currentPlanId = saved.planId, messageKey = "plan_saved")
                }
            }.onFailure {
                _state.update { it.copy(busy = false, saveComplete = false, messageKey = "save_failed") }
            }
        }
    }

    fun clearPhoto() {
        _state.update {
            it.copy(
                pendingPhoto = null,
                analysis = null,
                rejection = null,
                rejectedFaceConfidence = null,
                nodes = emptyList(),
                selectedNode = null,
                saveComplete = false,
                savedPlanVersion = null,
                currentPlanId = null,
                activePlanThresholdPercent = null,
                messageKey = null,
            )
        }
        clearHistory()
    }

    fun clearMessage() = _state.update { it.copy(messageKey = null) }

    fun loadPlan(snapshotId: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(busy = true, pendingPhoto = null, analysis = null, nodes = emptyList(), selectedNode = null, messageKey = null)
            }
            val loaded = runCatching {
                val plan = repository.getPlanSnapshot(snapshotId) ?: error("snapshot missing")
                val photo = repository.getPhoto(plan.photoId) ?: error("photo missing")
                val patient = repository.getPatient(plan.patientId) ?: error("patient missing")
                Triple(plan, photo, patient)
            }.getOrNull()
            if (loaded == null) {
                _state.update { it.copy(busy = false, messageKey = "plan_load_failed") }
                return@launch
            }
            val (plan, photo, patient) = loaded
            val landmarks = HairlineGeometry.landmarksFromJson(plan.landmarksJson)
            val pointMap = landmarks.associateBy(FacialLandmark::name)
            val eyeLeft = pointMap["LEFT_PUPIL"] ?: pointMap["LEFT_EYE_LOWER"]
            val eyeRight = pointMap["RIGHT_PUPIL"] ?: pointMap["RIGHT_EYE_LOWER"]
            val midline = if (eyeLeft != null && eyeRight != null) ((eyeLeft.x + eyeRight.x) / 2f).coerceIn(0f, 1f) else .5f
            val restoredAnalysis = DetectionAnalysis(
                faceConfidence = plan.faceConfidence,
                landmarks = landmarks,
                frontalScore = plan.frontalScore,
                eyeRotationDegrees = 0f,
                midlineX = midline,
                detectorFaceCount = 1,
            )
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(photo.originalBytes, 0, photo.originalBytes.size, options)
            val rotation = photo.exifRotation
            val width = if (rotation == 90 || rotation == 270) options.outHeight else options.outWidth
            val height = if (rotation == 90 || rotation == 270) options.outWidth else options.outHeight
            plansJob?.cancel()
            _state.update { current ->
                current.copy(
                    busy = false,
                    selectedPatient = patient,
                    pendingPhoto = PendingPhoto(photo.originalBytes, photo.source, photo.mimeType, width, height, rotation),
                    analysis = restoredAnalysis,
                    rejection = null,
                    rejectedFaceConfidence = null,
                    nodes = HairlineGeometry.nodesFromJson(
                        plan.nodesJson,
                        if (plan.balancedHandles) BezierHandleMode.MIRRORED else BezierHandleMode.INDEPENDENT,
                    ),
                    transform = HairlineGeometry.transformFromJson(plan.transformJson),
                    hairlineAppearance = HairlineGeometry.appearanceFromJson(plan.transformJson),
                    selectedNode = null,
                    currentPlanId = plan.planId,
                    activePlanThresholdPercent = plan.thresholdPercent,
                    hairlineReference = HairlineReference.entries.firstOrNull { it.name == plan.referenceMethod } ?: HairlineReference.FACIAL_PROPORTION,
                    hairlineProfile = HairlineProfile.entries.firstOrNull { it.name == plan.hairlineProfile } ?: HairlineProfile.CURRENT,
                    symmetryEnabled = plan.symmetryEnabled,
                    customRiseRatio = plan.customRiseRatio,
                    savedPlanVersion = plan.version,
                    saveComplete = true,
                )
            }
            plansJob = viewModelScope.launch {
                repository.observePlans(patient.id).collect { plans -> _state.update { it.copy(plans = plans) } }
            }
            clearHistory()
        }
    }

    private fun updateHistoryFlags() {
        _state.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
    }

    private fun editorAxis(state: PlannerUiState): FaceAxis? = state.analysis?.let {
        HairlineGeometry.facialAxis(it.landmarks, imageAspectRatio(state))?.let { axis ->
            HairlineGeometry.shiftedAxis(axis, state.transform.midlineOffset)
        }
    }

    private fun imageAspectRatio(state: PlannerUiState): Float = state.pendingPhoto?.let {
        it.width.toFloat() / it.height.coerceAtLeast(1)
    } ?: 1f

    private fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        updateHistoryFlags()
    }
}
