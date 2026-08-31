package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrtdk.glass.GlassBox
import com.mrtdk.glass.GlassContainer
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        val palettePrimary = MagicPalette.primaryColor(this)
        val paletteDark = MagicPalette.primaryVariantColor(this)
        val paletteAccent = MagicPalette.secondaryColor(this)
        window.statusBarColor = paletteDark
        window.navigationBarColor = paletteDark
        val showGlass = mutableStateOf(false)
        val animationStarted = mutableStateOf(false)
        setContent {
            MaterialTheme {
                LiquidGlassSplash(
                    palettePrimary = Color(palettePrimary),
                    paletteDark = Color(paletteDark),
                    paletteAccent = Color(paletteAccent),
                    showGlass = showGlass.value,
                    animationStarted = animationStarted.value,
                    onGlassDrawn = { animationStarted.value = true }
                ) {
                    startActivity(Intent(this, MainActivity::class.java))
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            }
        }
        val content = findViewById<android.view.View>(android.R.id.content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { systemSplash ->
                // Begin only after Android's starting window is genuinely off screen; a
                // Compose pre-draw may happen much earlier during a cold process launch.
                systemSplash.remove()
                content.post { showGlass.value = true }
            }
        } else {
            content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    content.postDelayed({ showGlass.value = true }, 100L)
                    return true
                }
            })
        }
    }

}

@Composable
private fun LiquidGlassSplash(
    palettePrimary: Color,
    paletteDark: Color,
    paletteAccent: Color,
    showGlass: Boolean,
    animationStarted: Boolean,
    onGlassDrawn: () -> Unit,
    onFinished: () -> Unit
) {
    val reveal = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }
    val backgroundScale = remember { Animatable(1.08f) }
    LaunchedEffect(animationStarted) {
        if (animationStarted) reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(animationStarted) {
        if (animationStarted) backgroundScale.animateTo(1f, tween(3000, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(animationStarted) {
        if (animationStarted) {
            progress.animateTo(1f, tween(3000, easing = LinearEasing))
        }
    }
    LaunchedEffect(animationStarted) {
        if (animationStarted) {
            // Navigation duration must not depend on the device's animator-duration setting.
            // Accessibility/developer settings may legitimately reduce visual animations to 0.
            delay(3000L)
            onFinished()
        }
    }

    val mtgFont = FontFamily(Font(R.font.mtg_title))
    if (!showGlass) {
        Box(Modifier.fillMaxSize().background(paletteDark))
        return
    }
    val localView = LocalView.current
    val glassFrameReported = remember { AtomicBoolean(false) }
    GlassContainer(
        modifier = Modifier.fillMaxSize().drawWithContent {
            drawContent()
            if (glassFrameReported.compareAndSet(false, true)) {
                localView.post(onGlassDrawn)
            }
        },
        // The fallback is intentional for startup: it preserves the library's glass gradient
        // while avoiding AGSL compilation before the first interactive frame.
        useShader = false,
        content = {
            Box(Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(R.drawable.mtgback),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = backgroundScale.value
                        scaleY = backgroundScale.value
                    }
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                paletteDark.copy(alpha = .48f),
                                paletteDark.copy(alpha = .72f),
                                palettePrimary.copy(alpha = .90f)
                            )
                        )
                    )
                )
            }
        },
        glassContent = {
            GlassBox(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp)),
                blur = .28f,
                scale = .04f,
                centerDistortion = .02f,
                tint = Color(0xA6F8F1E5),
                darkness = .04f,
                warpEdges = .05f,
                shape = RoundedCornerShape(30.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp, vertical = 34.dp)
                        .alpha(reveal.value)
                        .graphicsLayer {
                            scaleX = .82f + (.18f * reveal.value)
                            scaleY = .82f + (.18f * reveal.value)
                            translationY = (1f - reveal.value) * 28.dp.toPx()
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Canvas(Modifier.size(82.dp)) {
                        drawCircle(paletteAccent.copy(alpha = .34f), radius = size.minDimension / 2f)
                        drawArc(
                            color = paletteAccent,
                            startAngle = -90f,
                            sweepAngle = progress.value * 360f,
                            useCenter = false,
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawCircle(palettePrimary, radius = size.minDimension * .22f)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "MTG Biblio",
                        color = paletteDark,
                        fontFamily = mtgFont,
                        fontSize = 38.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Tu colección, siempre a mano",
                        color = paletteDark.copy(alpha = .86f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(28.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(palettePrimary.copy(alpha = .20f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.value)
                                .height(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(listOf(palettePrimary, paletteAccent)))
                        )
                    }
                }
            }
        }
    )
}
