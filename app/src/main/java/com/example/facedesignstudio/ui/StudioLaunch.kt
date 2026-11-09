package com.example.facedesignstudio.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

private val LaunchBackground = Color(0xFFF7F4FA)
private val LaunchInk = Color(0xFF64508C)
private val LaunchGold = Color(0xFFC79C66)

/** Brief branding on a fresh launch, never a route in the editor's back stack. */
@Composable
internal fun StudioLaunch(showOnLaunch: Boolean, content: @Composable () -> Unit) {
    var finished by rememberSaveable { mutableStateOf(!showOnLaunch) }
    var showContent by remember { mutableStateOf(finished || !showOnLaunch) }
    val context = LocalContext.current
    val motionEnabled = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val reveal = remember { Animatable(if (motionEnabled) 0f else 1f) }
    val opacity = remember { Animatable(1f) }
    val atmosphere = remember { Animatable(if (motionEnabled) 0f else .5f) }

    LaunchedEffect(Unit) {
        if (showOnLaunch && !finished && motionEnabled) {
            // Continue the background drift through the logo's pause and exit fade.
            atmosphere.animateTo(1f, tween(1910, easing = LinearEasing))
        }
    }

    LaunchedEffect(Unit) {
        if (!showOnLaunch || finished) return@LaunchedEffect
        if (motionEnabled) {
            reveal.animateTo(1f, tween(1450, easing = LinearEasing))
            delay(180)
        } else {
            // Honor disabled system animations without holding a long branded delay.
            delay(250)
        }
        showContent = true
        if (motionEnabled) opacity.animateTo(0f, tween(280))
        finished = true
    }

    Box(Modifier.fillMaxSize().background(LaunchBackground)) {
        if (showContent || !showOnLaunch) content()
        if (!finished && showOnLaunch) StudioSplash(
            progress = reveal.value,
            atmosphere = atmosphere.value,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = opacity.value }.pointerInput(Unit) {
                // Do not activate a control beneath the last frames of the transition.
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            },
        )
    }
}

@Composable
private fun StudioSplash(progress: Float, atmosphere: Float, modifier: Modifier = Modifier) {
    fun phase(start: Float, end: Float) = FastOutSlowInEasing.transform(((progress-start)/(end-start)).coerceIn(0f,1f))
    val portrait = phase(0f,.46f)
    val hairline = phase(.18f,.86f)
    val lettering = phase(.30f,.78f)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(modifier.background(LaunchBackground).clearAndSetSemantics {
            contentDescription = "چهره‌پرداز، استودیو طراحی چهره"
        }) {
            Canvas(Modifier.fillMaxSize()) {
                // Soft light instead of flat discs. All motion shares the finite launch
                // timeline, so reduced motion stays static and launch time is unchanged.
                val drift = atmosphere * 64.dp.toPx()
                val sweep = sin(atmosphere * PI).toFloat()
                drawRect(Brush.verticalGradient(listOf(Color(0xFFF0E9FA), LaunchBackground, Color(0xFFFAF1E8))))
                fun glow(color: Color, center: Offset, radius: Float) {
                    drawRect(Brush.radialGradient(listOf(color, color.copy(alpha=0f)), center, radius))
                }
                glow(Color(0xFFAB84D4).copy(alpha=.46f),Offset(size.width*.94f-drift*1.8f,size.height*(.10f+.07f*sweep)),size.width*.85f)
                glow(Color(0xFFE2BB85).copy(alpha=.40f),Offset(size.width*.02f+drift*1.5f,size.height*(.88f-.08f*sweep)),size.width*.80f)
                glow(Color(0xFFC9B7E9).copy(alpha=.22f),Offset(size.width*(.15f+.65f*atmosphere),size.height*.25f),size.width*.48f)
                glow(Color.White.copy(alpha=.85f),Offset(size.width*.48f,size.height*.46f),size.width*.75f)

                // Fine, flowing ribbons frame the brand without running behind the title.
                for (i in 0..2) {
                    val shift = i * 12.dp.toPx()
                    val upper = Path().apply {
                        moveTo(-size.width*.18f,size.height*.24f+shift+drift)
                        cubicTo(size.width*.22f,size.height*.05f+shift,
                            size.width*.72f,size.height*.31f+shift,size.width*1.18f,size.height*.10f+shift-drift)
                    }
                    val lower = Path().apply {
                        moveTo(-size.width*.16f,size.height*.84f+shift)
                        cubicTo(size.width*.30f,size.height*.68f+shift+drift,
                            size.width*.62f,size.height*.98f+shift,size.width*1.16f,size.height*.77f+shift)
                    }
                    val ribbon = Brush.horizontalGradient(listOf(LaunchGold.copy(alpha=0f),
                        LaunchGold.copy(alpha=.24f-i*.045f),Color.White.copy(alpha=.65f),LaunchInk.copy(alpha=.06f)))
                    drawPath(upper,ribbon,style=Stroke((if(i==0) 1.1f else .55f).dp.toPx()))
                    drawPath(lower,ribbon,style=Stroke((if(i==0) 1.1f else .55f).dp.toPx()))
                    if (i == 0) {
                        fun cubic(a: Offset,b: Offset,c: Offset,d: Offset,t: Float): Offset {
                            val u=1f-t
                            return a*(u*u*u)+b*(3f*u*u*t)+c*(3f*u*t*t)+d*(t*t*t)
                        }
                        fun light(point: Offset) {
                            val radius=18.dp.toPx()
                            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=.8f),
                                LaunchGold.copy(alpha=.16f),Color.Transparent),point,radius),radius,point)
                            drawCircle(Color.White.copy(alpha=.9f),1.8.dp.toPx(),point)
                        }
                        light(cubic(Offset(-size.width*.18f,size.height*.24f+drift),
                            Offset(size.width*.22f,size.height*.05f),Offset(size.width*.72f,size.height*.31f),
                            Offset(size.width*1.18f,size.height*.10f-drift),.12f+.72f*atmosphere))
                        light(cubic(Offset(-size.width*.16f,size.height*.84f),
                            Offset(size.width*.30f,size.height*.68f+drift),Offset(size.width*.62f,size.height*.98f),
                            Offset(size.width*1.16f,size.height*.77f),.88f-.70f*atmosphere))
                    }
                }
                // Deterministic, sparse dust at the edges; no random flicker per frame.
                repeat(18) { i ->
                    val x = size.width * (if(i%2==0) .06f+(i%5)*.035f else .80f+(i%5)*.032f) +
                        sin(atmosphere*PI+i).toFloat()*8.dp.toPx()
                    val y = size.height * (.10f+((i*7)%19)*.043f) - drift*(.35f+(i%3)*.2f)
                    val shimmer = .65f+.35f*cos(atmosphere*PI*1.3+i*.7).toFloat()
                    val ink = if(i%3==0) LaunchGold else LaunchInk
                    val point = Offset(x,y)
                    drawCircle(ink.copy(alpha=.16f*shimmer),(if(i%4==0) 2f else 1f).dp.toPx(),point)
                    if(i%6==0) {
                        val r=4.dp.toPx()
                        drawLine(ink.copy(alpha=.20f*shimmer),point-Offset(r,0f),point+Offset(r,0f),.6.dp.toPx())
                        drawLine(ink.copy(alpha=.20f*shimmer),point-Offset(0f,r),point+Offset(0f,r),.6.dp.toPx())
                    }
                }
            }
            val compact = maxHeight < 440.dp
            val emblemSize = (maxHeight * .30f).coerceIn(90.dp,240.dp)
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=28.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.Center,
            ) {
                Canvas(Modifier.size(emblemSize).graphicsLayer {
                    scaleX=.96f+.04f*portrait; scaleY=scaleX
                }) {
                    val unit=size.minDimension/200f
                    scale(unit,unit,pivot=Offset.Zero) {
                        drawCircle(Color(0xFFEDE7F5),94f,Offset(100f,100f))
                        drawCircle(LaunchInk.copy(alpha=.09f),99f,Offset(100f,100f),style=Stroke(.7f))
                        drawLine(LaunchGold.copy(alpha=.24f*portrait),Offset(100f,23f),Offset(100f,167f),.8f,
                            pathEffect=PathEffect.dashPathEffect(floatArrayOf(2f,5f)))
                        val face=Path().apply {
                            moveTo(48f,86f)
                            cubicTo(45f,119f,63f,150f,86f,164f)
                            cubicTo(94f,170f,106f,170f,114f,164f)
                            cubicTo(137f,150f,155f,119f,152f,86f)
                        }
                        val measured=PathMeasure()
                        fun trace(path:Path,amount:Float,color:Color,width:Float) {
                            measured.setPath(path,false)
                            val segment=Path()
                            measured.getSegment(0f,measured.length*amount,segment,true)
                            drawPath(segment,color,style=Stroke(width,cap=StrokeCap.Round))
                        }
                        trace(face,portrait,LaunchInk.copy(alpha=.65f),1.6f)
                        val features=Path().apply {
                            moveTo(69f,99f);cubicTo(76f,95f,83f,95f,89f,99f)
                            moveTo(111f,99f);cubicTo(117f,95f,124f,95f,131f,99f)
                            moveTo(100f,103f);lineTo(97f,120f);quadraticTo(100f,123f,104f,120f)
                            moveTo(90f,139f);quadraticTo(100f,143f,110f,139f)
                        }
                        drawPath(features,LaunchInk.copy(alpha=.35f*portrait),style=Stroke(1.25f,cap=StrokeCap.Round))
                        val line=Path().apply {
                            moveTo(42f,88f)
                            cubicTo(52f,74f,63f,51f,82f,57f)
                            cubicTo(92f,60f,95f,55f,100f,53f)
                            cubicTo(105f,55f,108f,60f,118f,57f)
                            cubicTo(137f,51f,148f,74f,158f,88f)
                        }
                        drawPath(line,LaunchInk.copy(alpha=.10f),style=Stroke(2.8f,cap=StrokeCap.Round))
                        trace(line,hairline,LaunchInk,2.8f)
                        listOf(Offset(42f,88f),Offset(100f,53f),Offset(158f,88f)).forEachIndexed { i,p ->
                            val appear=phase(.24f+i*.20f,.44f+i*.20f)
                            drawCircle(LaunchBackground,5.2f*appear,p)
                            drawCircle(if(i==1) LaunchGold else LaunchInk,3f*appear,p)
                        }
                        // One quiet finishing halo; no perpetual loading animation.
                        val halo=phase(.68f,1f)
                        drawCircle(LaunchGold.copy(alpha=(1f-halo)*.24f),6f+10f*halo,Offset(100f,53f),style=Stroke(1f))
                    }
                }
                Spacer(Modifier.height(if(compact) 14.dp else 28.dp))
                Column(Modifier.graphicsLayer {
                    alpha=lettering
                    translationY=(1f-lettering)*10.dp.toPx()
                }, horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("چهره‌پرداز",color=Color(0xFF392851),fontSize=if(compact) 28.sp else 36.sp,
                        fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("استودیو طراحی چهره",color=Color(0xFF80738E),fontSize=15.sp,textAlign=TextAlign.Center)
                    if (!compact) {
                        Spacer(Modifier.height(20.dp))
                        Text("FACE DESIGN STUDIO",color=LaunchInk.copy(alpha=.55f),fontSize=10.sp,
                            letterSpacing=3.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center)
                    }
                }
            }
            if (!compact) Text("هر چهره، طرحی منحصر‌به‌فرد",
                Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal=24.dp,vertical=28.dp)
                    .graphicsLayer { alpha=lettering },
                color=Color(0xFF95899E),fontSize=12.sp,textAlign=TextAlign.Center)
        }
    }
}
