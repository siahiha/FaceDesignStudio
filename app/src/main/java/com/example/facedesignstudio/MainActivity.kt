package com.example.facedesignstudio

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facedesignstudio.ui.FaceDesignApp
import com.example.facedesignstudio.ui.PlannerViewModel
import com.example.facedesignstudio.ui.StudioLaunch
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: PlannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_FaceDesignStudio)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK),
        )
        // Load local state while the brief launch animation is on screen.
        val plannerViewModel = viewModel
        setContent {
            PlannerTheme {
                StudioLaunch(showOnLaunch = savedInstanceState == null) {
                    FaceDesignApp(plannerViewModel)
                }
            }
        }
    }
}

@Composable
private fun PlannerTheme(content: @Composable () -> Unit) {
    val palette = lightColorScheme(
        primary = Color(0xFF64508C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE7F5),
        onPrimaryContainer = Color(0xFF392851),
        secondary = Color(0xFF80715D),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF1EBE1),
        onSecondaryContainer = Color(0xFF402811),
        tertiary = Color(0xFF596A89),
        background = Color(0xFFF7F6F9),
        onBackground = Color(0xFF26232D),
        surface = Color.White,
        onSurface = Color(0xFF26232D),
        surfaceVariant = Color(0xFFEEECF2),
        surfaceContainerLow = Color(0xFFF2F0F5),
        onSurfaceVariant = Color(0xFF706B79),
        outline = Color(0xFF8D8697),
        outlineVariant = Color(0xFFE4E0EA),
        error = Color(0xFFB3261E),
        errorContainer = Color(0xFFF9DEDC),
    )
    val baseTypography = Typography()
    MaterialTheme(
        colorScheme = palette,
        typography = Typography(
            headlineMedium = baseTypography.headlineMedium.copy(fontSize = 29.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
            headlineSmall = baseTypography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
            titleLarge = baseTypography.titleLarge.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = baseTypography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = baseTypography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = baseTypography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = baseTypography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
