package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlin.math.hypot

// Neon branding colors requested
val NeonCyan = Color(0xFF00FFFF)
val NeonOrange = Color(0xFFFFA500)
val CircuitTraceColor = Color(0x350052FF)
val CircuitNodeColor = Color(0x700052FF)

/**
 * DataFlowLoadingIndicator:
 * A high-tech native Jetpack Compose animation using Canvas.
 * - Center Logo: Pulses in and out smoothly using rememberInfiniteTransition.
 * - Circuit Canvas: Draws minimal circuit traces with 90-degree right angles from screen edges to the center.
 * - Data Particles: Glows neon cyan and orange, moving continuously inward into the Zero Pay logo.
 */
@Composable
fun DataFlowLoadingIndicator(
    modifier: Modifier = Modifier,
    title: String = "Syncing SMS Transactions...",
    subtitle: String = "Zero Pay Gateway • Secure Pipeline",
    logoSize: Dp = 100.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DataFlowInfiniteTransition")

    // Pulse animation for central logo (scaling 0.94f to 1.06f)
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoPulseScale"
    )

    // Glowing aura expanding and fading around center logo
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AuraScale"
    )
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AuraAlpha"
    )

    // Primary particle progress moving from 0.0f (outer edge) to 1.0f (center logo)
    val particleProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ParticleProgress1"
    )

    // Staggered secondary particle progress for continuous stream
    val particleProgress2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ParticleProgress2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.95f))
            .testTag("data_flow_loading_indicator"),
        contentAlignment = Alignment.Center
    ) {
        // 1. Circuit & Data Particle Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2f, height / 2f)
            val centerRadius = (logoSize.toPx() / 2f) + 12f

            // Define 8 multi-segment circuit paths with orthogonal 90-degree right angles
            val paths = listOf(
                // 1. Top-Left edge -> bend -> center
                CircuitPath(
                    points = listOf(
                        Offset(width * 0.12f, 0f),
                        Offset(width * 0.12f, center.y - 120f),
                        Offset(center.x - centerRadius * 0.8f, center.y - centerRadius * 0.6f)
                    ),
                    particleColor = NeonCyan
                ),
                // 2. Top-Center edge -> straight -> center
                CircuitPath(
                    points = listOf(
                        Offset(center.x, 0f),
                        Offset(center.x, center.y - centerRadius)
                    ),
                    particleColor = NeonOrange
                ),
                // 3. Top-Right edge -> bend -> center
                CircuitPath(
                    points = listOf(
                        Offset(width * 0.88f, 0f),
                        Offset(width * 0.88f, center.y - 120f),
                        Offset(center.x + centerRadius * 0.8f, center.y - centerRadius * 0.6f)
                    ),
                    particleColor = NeonCyan
                ),
                // 4. Left edge -> bend -> center
                CircuitPath(
                    points = listOf(
                        Offset(0f, center.y * 0.65f),
                        Offset(center.x * 0.4f, center.y * 0.65f),
                        Offset(center.x * 0.4f, center.y),
                        Offset(center.x - centerRadius, center.y)
                    ),
                    particleColor = NeonOrange
                ),
                // 5. Right edge -> bend -> center
                CircuitPath(
                    points = listOf(
                        Offset(width, center.y * 0.65f),
                        Offset(width - center.x * 0.4f, center.y * 0.65f),
                        Offset(width - center.x * 0.4f, center.y),
                        Offset(center.x + centerRadius, center.y)
                    ),
                    particleColor = NeonCyan
                ),
                // 6. Bottom-Left edge -> bend -> center
                CircuitPath(
                    points = listOf(
                        Offset(width * 0.15f, height),
                        Offset(width * 0.15f, center.y + 130f),
                        Offset(center.x - centerRadius * 0.8f, center.y + centerRadius * 0.6f)
                    ),
                    particleColor = NeonOrange
                ),
                // 7. Bottom-Center edge -> straight -> center
                CircuitPath(
                    points = listOf(
                        Offset(center.x, height),
                        Offset(center.x, center.y + centerRadius)
                    ),
                    particleColor = NeonCyan
                ),
                // 8. Bottom-Right edge -> bend -> center
                CircuitPath(
                    points = listOf(
                        Offset(width * 0.85f, height),
                        Offset(width * 0.85f, center.y + 130f),
                        Offset(center.x + centerRadius * 0.8f, center.y + centerRadius * 0.6f)
                    ),
                    particleColor = NeonOrange
                )
            )

            // Draw circuit traces and terminal nodes
            paths.forEach { circuit ->
                val points = circuit.points
                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = CircuitTraceColor,
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw circular nodes at right-angle junctions
                for (i in 1 until points.size - 1) {
                    drawCircle(
                        color = CircuitNodeColor,
                        radius = 4.5f,
                        center = points[i]
                    )
                }
            }

            // Draw moving data particles along paths
            paths.forEachIndexed { index, circuit ->
                val progress = if (index % 2 == 0) particleProgress1 else particleProgress2
                val pos = circuit.interpolate(progress)

                // Particle glow (halo)
                drawCircle(
                    color = circuit.particleColor.copy(alpha = 0.35f),
                    radius = 9f,
                    center = pos
                )
                // Particle bright core
                drawCircle(
                    color = circuit.particleColor,
                    radius = 4.5f,
                    center = pos
                )

                // Trail particle slightly behind for momentum effect
                val trailProgress = (progress - 0.08f).coerceAtLeast(0f)
                val trailPos = circuit.interpolate(trailProgress)
                drawCircle(
                    color = circuit.particleColor.copy(alpha = 0.25f),
                    radius = 3.0f,
                    center = trailPos
                )
            }

            // Draw central receiving aura ring around logo
            drawCircle(
                color = Color(0xFF0052FF).copy(alpha = auraAlpha),
                radius = centerRadius * auraScale,
                center = center,
                style = Stroke(width = 3.5f)
            )
        }

        // 2. Central Logo with Subtle Pulse & Status Information
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(logoSize)
                    .scale(logoScale)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_white_bg),
                    contentDescription = "Zero Pay Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 2.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Multi-segment orthogonal circuit path helper for interpolation.
 */
private class CircuitPath(
    val points: List<Offset>,
    val particleColor: Color
) {
    private val segmentLengths: List<Float>
    val totalLength: Float

    init {
        val lengths = mutableListOf<Float>()
        var sum = 0f
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val len = hypot(p2.x - p1.x, p2.y - p1.y)
            lengths.add(len)
            sum += len
        }
        segmentLengths = lengths
        totalLength = sum
    }

    fun interpolate(progress: Float): Offset {
        if (points.size <= 1 || totalLength <= 0f) return points.firstOrNull() ?: Offset.Zero
        val targetDist = (progress.coerceIn(0f, 1f)) * totalLength
        var accumulated = 0f

        for (i in 0 until points.size - 1) {
            val len = segmentLengths[i]
            if (targetDist <= accumulated + len || i == points.size - 2) {
                val segDist = targetDist - accumulated
                val fraction = if (len > 0f) (segDist / len).coerceIn(0f, 1f) else 0f
                val p1 = points[i]
                val p2 = points[i + 1]
                return Offset(
                    x = p1.x + (p2.x - p1.x) * fraction,
                    y = p1.y + (p2.y - p1.y) * fraction
                )
            }
            accumulated += len
        }
        return points.last()
    }
}
