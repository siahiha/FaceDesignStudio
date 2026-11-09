package com.example.facedesignstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class StudioSymbol { Plus, Back, Search, Settings, Camera, Gallery, Edit, Undo, Redo, Eye, Export, Check, Nodes, Line, Align, Folder }

/** Small, consistent outline icons without a new icon-library dependency. */
@Composable
internal fun StudioIcon(symbol: StudioSymbol, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        val stroke = 1.8f * unit
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, point(x, y), point(x2, y2), stroke, StrokeCap.Round)
        fun circle(x: Float, y: Float, r: Float) = drawCircle(tint, r * unit, point(x, y), style = Stroke(stroke))
        fun box(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(tint, point(x, y), Size(w * unit, h * unit), androidx.compose.ui.geometry.CornerRadius(2f * unit), style = Stroke(stroke))
        when (symbol) {
            StudioSymbol.Plus -> { line(5f,12f,19f,12f); line(12f,5f,12f,19f) }
            StudioSymbol.Back -> { line(5f,12f,19f,12f); line(13f,6f,19f,12f); line(13f,18f,19f,12f) }
            StudioSymbol.Search -> { circle(10f,10f,6f); line(15f,15f,21f,21f) }
            StudioSymbol.Settings -> { line(4f,7f,20f,7f); line(4f,17f,20f,17f); circle(9f,7f,2.5f); circle(15f,17f,2.5f) }
            StudioSymbol.Camera -> { box(3f,6f,18f,14f); circle(12f,13f,4f); line(8f,6f,9f,3f); line(9f,3f,15f,3f); line(15f,3f,16f,6f) }
            StudioSymbol.Gallery -> { box(3f,3f,18f,18f); circle(8f,8f,1.5f); line(4f,18f,10f,12f); line(10f,12f,14f,16f); line(14f,16f,17f,12f); line(17f,12f,21f,16f) }
            StudioSymbol.Edit -> { line(5f,16f,16f,5f); line(8f,19f,19f,8f); line(16f,5f,19f,8f); line(5f,16f,4f,20f); line(4f,20f,8f,19f) }
            StudioSymbol.Undo, StudioSymbol.Redo -> {
                val right = symbol == StudioSymbol.Redo
                fun x(v: Float) = if (right) 24f-v else v
                val path = Path().apply { moveTo(x(5f)*unit,9f*unit); cubicTo(x(19f)*unit,3f*unit,x(23f)*unit,19f*unit,x(12f)*unit,19f*unit) }
                drawPath(path,tint,style=Stroke(stroke, cap=StrokeCap.Round))
                line(x(5f),4f,x(5f),9f); line(x(5f),9f,x(10f),10f)
            }
            StudioSymbol.Eye -> {
                val path = Path().apply { moveTo(2f*unit,12f*unit); cubicTo(7f*unit,3f*unit,17f*unit,3f*unit,22f*unit,12f*unit); cubicTo(17f*unit,21f*unit,7f*unit,21f*unit,2f*unit,12f*unit) }
                drawPath(path,tint,style=Stroke(stroke)); circle(12f,12f,3f)
            }
            StudioSymbol.Export -> { line(12f,3f,12f,15f); line(8f,11f,12f,15f); line(16f,11f,12f,15f); line(4f,16f,4f,21f); line(4f,21f,20f,21f); line(20f,21f,20f,16f) }
            StudioSymbol.Check -> { line(5f,12f,10f,17f); line(10f,17f,20f,6f) }
            StudioSymbol.Nodes -> { line(5f,17f,12f,7f); line(12f,7f,20f,16f); circle(5f,17f,2f); circle(12f,7f,2f); circle(20f,16f,2f) }
            StudioSymbol.Line -> {
                val path = Path().apply { moveTo(3f*unit,17f*unit); cubicTo(6f*unit,1f*unit,18f*unit,23f*unit,21f*unit,7f*unit) }
                drawPath(path,tint,style=Stroke(stroke, cap=StrokeCap.Round))
            }
            StudioSymbol.Align -> { line(12f,3f,12f,21f); line(4f,8f,8f,8f); line(16f,8f,20f,8f); line(4f,16f,8f,16f); line(16f,16f,20f,16f) }
            StudioSymbol.Folder -> { box(3f,7f,18f,14f); line(3f,7f,3f,3f); line(3f,3f,10f,3f); line(10f,3f,13f,7f) }
        }
    }
}

@Composable
internal fun StudioHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StudioPrimary(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun StudioAction(title: String, subtitle: String, symbol: StudioSymbol, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                StudioIcon(symbol, Modifier.padding(14.dp), MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun StudioSteps(active: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("تصویر", "طراحی", "ذخیره").forEachIndexed { index, label ->
            if (index > 0) HorizontalDivider(Modifier.weight(1f).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = CircleShape, color = if (index <= active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (index < active) StudioIcon(StudioSymbol.Check, Modifier.size(14.dp), Color.White)
                        else Text(listOf("۱", "۲", "۳")[index], style = MaterialTheme.typography.labelSmall, color = if (index == active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text("تمام") }
            }
            content()
        }
    }
}

@Composable
internal fun StudioToggle(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChecked, Modifier.semantics { contentDescription = title })
    }
}
