package com.example.ui.muscles

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BodyModel
import com.example.data.model.Muscle
import com.example.data.model.MuscleIntensity
import com.example.data.model.intensityToColor
import kotlin.math.pow

private const val DEBUG_HITBOXES = false

enum class BodyView {
    FRONT,
    BACK
}

@Composable
fun BodyHeatMapCanvas(
    bodyModel: BodyModel,
    heatmap: Map<Muscle, MuscleIntensity>,
    selected: Muscle?,
    onSelect: (Muscle?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            AnatomyPanel(
                label = "Frente",
                bodyView = BodyView.FRONT,
                bodyModel = bodyModel,
                heatmap = heatmap,
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )

            AnatomyPanel(
                label = "Espalda",
                bodyView = BodyView.BACK,
                bodyModel = bodyModel,
                heatmap = heatmap,
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AnatomyPanel(
    label: String,
    bodyView: BodyView,
    bodyModel: BodyModel,
    heatmap: Map<Muscle, MuscleIntensity>,
    selected: Muscle?,
    onSelect: (Muscle?) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageRes = remember(bodyView, bodyModel) {
        getBodyImageRes(bodyModel = bodyModel, bodyView = bodyView)
    }
    val imageAspectRatio = remember(bodyView, bodyModel) {
        bodyImageAspectRatio(bodyModel, bodyView)
    }
    val regions = remember(bodyView, bodyModel) {
        regionsFor(bodyModel, bodyView)
    }
    var debugTap by remember { mutableStateOf<Offset?>(null) }
    var debugHit by remember { mutableStateOf<Muscle?>(null) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(imageAspectRatio)
                .pointerInput(bodyModel, bodyView, imageAspectRatio) {
                    detectTapGestures { tap ->
                        if (DEBUG_HITBOXES) {
                            debugTap = tap
                        }

                        val imageBounds = calculateImageDrawBounds(
                            containerSize = Size(size.width.toFloat(), size.height.toFloat()),
                            imageAspectRatio = imageAspectRatio
                        )

                        if (!imageBounds.contains(tap)) {
                            debugHit = null
                            onSelect(null)
                            return@detectTapGestures
                        }

                        val xNorm = (tap.x - imageBounds.left) / imageBounds.width
                        val yNorm = (tap.y - imageBounds.top) / imageBounds.height
                        val hit = hitTestMuscle(
                            xNorm = xNorm,
                            yNorm = yNorm,
                            model = bodyModel,
                            view = bodyView
                        )
                        debugHit = hit
                        onSelect(hit)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val imageBounds = calculateImageDrawBounds(size, imageAspectRatio)
                val bodyBounds = bodyBoundsForImage(imageBounds, bodyModel, bodyView)
                drawMuscleRegions(
                    regions = regions,
                    bodyBounds = bodyBounds,
                    imageBounds = imageBounds,
                    heatmap = heatmap,
                    selected = selected,
                    debugTap = debugTap,
                    debugHit = debugHit
                )
            }
        }
    }
}

fun hitTestMuscle(
    xNorm: Float,
    yNorm: Float,
    model: BodyModel,
    view: BodyView
): Muscle? {
    val crop = bodyImageCrop(model, view)
    if (!crop.contains(Offset(xNorm, yNorm))) return null

    val bodyPoint = Offset(
        x = (xNorm - crop.left) / crop.width,
        y = (yNorm - crop.top) / crop.height
    )

    return regionsFor(model, view)
        .asSequence()
        .filter { region -> region.contains(bodyPoint) }
        .maxByOrNull { it.priority }
        ?.muscle
}

private fun getBodyImageRes(
    bodyModel: BodyModel,
    bodyView: BodyView
): Int {
    return when {
        bodyModel == BodyModel.MALE && bodyView == BodyView.FRONT -> R.drawable.body_male_front
        bodyModel == BodyModel.MALE && bodyView == BodyView.BACK -> R.drawable.body_male_back
        bodyModel == BodyModel.FEMALE && bodyView == BodyView.FRONT -> R.drawable.body_female_front
        else -> R.drawable.body_female_back
    }
}

private fun bodyImageAspectRatio(
    bodyModel: BodyModel,
    bodyView: BodyView
): Float {
    return when {
        bodyModel == BodyModel.MALE && bodyView == BodyView.FRONT -> 682f / 1387f
        bodyModel == BodyModel.MALE && bodyView == BodyView.BACK -> 647f / 1386f
        bodyModel == BodyModel.FEMALE && bodyView == BodyView.FRONT -> 634f / 1370f
        else -> 613f / 1367f
    }
}

private fun bodyImageCrop(
    bodyModel: BodyModel,
    bodyView: BodyView
): Rect {
    return when {
        bodyModel == BodyModel.MALE && bodyView == BodyView.FRONT -> Rect(0.01f, 0.00f, 0.99f, 0.995f)
        bodyModel == BodyModel.MALE && bodyView == BodyView.BACK -> Rect(0.02f, 0.00f, 0.98f, 0.995f)
        bodyModel == BodyModel.FEMALE && bodyView == BodyView.FRONT -> Rect(0.02f, 0.00f, 0.98f, 0.995f)
        else -> Rect(0.01f, 0.00f, 0.99f, 0.995f)
    }
}

private fun calculateImageDrawBounds(
    containerSize: Size,
    imageAspectRatio: Float
): Rect {
    val containerAspect = containerSize.width / containerSize.height
    val imageDrawWidth: Float
    val imageDrawHeight: Float

    if (containerAspect > imageAspectRatio) {
        imageDrawHeight = containerSize.height
        imageDrawWidth = imageDrawHeight * imageAspectRatio
    } else {
        imageDrawWidth = containerSize.width
        imageDrawHeight = imageDrawWidth / imageAspectRatio
    }

    val imageOffsetX = (containerSize.width - imageDrawWidth) / 2f
    val imageOffsetY = (containerSize.height - imageDrawHeight) / 2f
    return Rect(
        left = imageOffsetX,
        top = imageOffsetY,
        right = imageOffsetX + imageDrawWidth,
        bottom = imageOffsetY + imageDrawHeight
    )
}

private fun bodyBoundsForImage(
    imageBounds: Rect,
    bodyModel: BodyModel,
    bodyView: BodyView
): Rect {
    return imageBounds.scaleTo(bodyImageCrop(bodyModel, bodyView))
}

private data class MuscleHitRegion(
    val muscle: Muscle,
    val shapes: List<HitShape>,
    val priority: Int = 0
) {
    fun contains(point: Offset): Boolean = shapes.any { it.contains(point) }
}

private sealed class HitShape {
    abstract fun contains(point: Offset): Boolean
    abstract fun draw(
        drawScope: DrawScope,
        bodyBounds: Rect,
        color: Color,
        style: DrawStyle
    )

    abstract fun center(): Offset

    data class Oval(val rect: Rect) : HitShape() {
        override fun contains(point: Offset): Boolean {
            val rx = rect.width / 2f
            val ry = rect.height / 2f
            if (rx <= 0f || ry <= 0f) return false

            val cx = rect.left + rx
            val cy = rect.top + ry
            val dx = ((point.x - cx) / rx).pow(2)
            val dy = ((point.y - cy) / ry).pow(2)
            return dx + dy <= 1f
        }

        override fun draw(
            drawScope: DrawScope,
            bodyBounds: Rect,
            color: Color,
            style: DrawStyle
        ) {
            val drawRect = bodyBounds.scaleTo(rect)
            drawScope.drawOval(
                color = color,
                topLeft = Offset(drawRect.left, drawRect.top),
                size = Size(drawRect.width, drawRect.height),
                style = style
            )
        }

        override fun center(): Offset = Offset(rect.left + rect.width / 2f, rect.top + rect.height / 2f)
    }

    data class Polygon(val points: List<Offset>) : HitShape() {
        override fun contains(point: Offset): Boolean = polygonContains(point, points)

        override fun draw(
            drawScope: DrawScope,
            bodyBounds: Rect,
            color: Color,
            style: DrawStyle
        ) {
            drawScope.drawPath(
                path = points.toCanvasPath(bodyBounds),
                color = color,
                style = style
            )
        }

        override fun center(): Offset {
            val x = points.sumOf { it.x.toDouble() }.toFloat() / points.size
            val y = points.sumOf { it.y.toDouble() }.toFloat() / points.size
            return Offset(x, y)
        }
    }
}

private fun DrawScope.drawMuscleRegions(
    regions: List<MuscleHitRegion>,
    bodyBounds: Rect,
    imageBounds: Rect,
    heatmap: Map<Muscle, MuscleIntensity>,
    selected: Muscle?,
    debugTap: Offset?,
    debugHit: Muscle?
) {
    regions.forEach { region ->
        val intensity = intensityFor(region, heatmap)
        if (intensity > 0) {
            val color = Color(intensityToColor(intensity)).copy(alpha = intensityAlpha(intensity))
            drawRegion(region, bodyBounds, color, Fill)
        }

        if (selected == region.muscle) {
            drawRegion(region, bodyBounds, Color.White.copy(alpha = 0.96f), Stroke(width = 3.2f))
            drawRegion(region, bodyBounds, Color(0xFF2563EB).copy(alpha = 0.94f), Stroke(width = 1.5f))
        }

        if (DEBUG_HITBOXES) {
            drawRegion(region, bodyBounds, Color(0xFFE11D48).copy(alpha = 0.70f), Stroke(width = 1.2f))
            drawDebugLabel(region, bodyBounds)
        }
    }

    if (DEBUG_HITBOXES) {
        drawRect(
            color = Color(0xFF22C55E),
            topLeft = Offset(imageBounds.left, imageBounds.top),
            size = Size(imageBounds.width, imageBounds.height),
            style = Stroke(width = 1.5f)
        )
        drawRect(
            color = Color(0xFF3B82F6),
            topLeft = Offset(bodyBounds.left, bodyBounds.top),
            size = Size(bodyBounds.width, bodyBounds.height),
            style = Stroke(width = 1.5f)
        )
        debugTap?.let { tap ->
            drawCircle(Color(0xFFF97316), radius = 7f, center = tap)
            drawLine(Color(0xFFF97316), Offset(tap.x - 12f, tap.y), Offset(tap.x + 12f, tap.y), 2f)
            drawLine(Color(0xFFF97316), Offset(tap.x, tap.y - 12f), Offset(tap.x, tap.y + 12f), 2f)
        }
        debugHit?.let { hit ->
            drawContext.canvas.nativeCanvas.drawText(
                hit.displayName,
                10f,
                28f,
                debugPaint(Color(0xFF111827))
            )
        }
    }
}

private fun DrawScope.drawRegion(
    region: MuscleHitRegion,
    bodyBounds: Rect,
    color: Color,
    style: DrawStyle
) {
    region.shapes.forEach { shape ->
        shape.draw(this, bodyBounds, color, style)
    }
}

private fun DrawScope.drawDebugLabel(region: MuscleHitRegion, bodyBounds: Rect) {
    val center = region.shapes.firstOrNull()?.center()?.toCanvasPoint(bodyBounds) ?: return
    drawContext.canvas.nativeCanvas.drawText(
        region.muscle.displayName,
        center.x,
        center.y,
        debugPaint(Color(0xFFE11D48))
    )
}

private fun intensityFor(region: MuscleHitRegion, heatmap: Map<Muscle, MuscleIntensity>): Int {
    val direct = heatmap[region.muscle]?.intensity
    if (direct != null) return direct
    return if (region.muscle == Muscle.TRAPS) {
        heatmap[Muscle.BACK]?.intensity ?: 0
    } else {
        0
    }
}

private fun intensityAlpha(intensity: Int): Float {
    return when {
        intensity <= 0 -> 0.00f
        intensity <= 25 -> 0.18f
        intensity <= 50 -> 0.25f
        intensity <= 75 -> 0.32f
        else -> 0.40f
    }
}

private fun regionsFor(model: BodyModel, view: BodyView): List<MuscleHitRegion> {
    return when {
        model == BodyModel.MALE && view == BodyView.FRONT -> maleFrontRegions()
        model == BodyModel.MALE && view == BodyView.BACK -> maleBackRegions()
        model == BodyModel.FEMALE && view == BodyView.FRONT -> femaleFrontRegions()
        else -> femaleBackRegions()
    }
}

private fun maleFrontRegions(): List<MuscleHitRegion> {
    return listOf(
        MuscleHitRegion(Muscle.SHOULDERS, listOf(oval(0.17f, 0.155f, 0.315f, 0.270f), oval(0.685f, 0.155f, 0.830f, 0.270f)), 90),
        MuscleHitRegion(Muscle.CHEST, listOf(poly(p(0.315f, 0.215f), p(0.500f, 0.195f), p(0.498f, 0.330f), p(0.355f, 0.350f), p(0.285f, 0.300f)), poly(p(0.685f, 0.215f), p(0.500f, 0.195f), p(0.502f, 0.330f), p(0.645f, 0.350f), p(0.715f, 0.300f))), 45),
        MuscleHitRegion(Muscle.BICEPS, listOf(oval(0.120f, 0.250f, 0.250f, 0.425f), oval(0.750f, 0.250f, 0.880f, 0.425f)), 85),
        MuscleHitRegion(Muscle.FOREARMS, listOf(poly(p(0.140f, 0.385f), p(0.235f, 0.360f), p(0.285f, 0.520f), p(0.200f, 0.560f), p(0.125f, 0.495f)), poly(p(0.860f, 0.385f), p(0.765f, 0.360f), p(0.715f, 0.520f), p(0.800f, 0.560f), p(0.875f, 0.495f))), 88),
        MuscleHitRegion(Muscle.ABS, listOf(poly(p(0.405f, 0.330f), p(0.595f, 0.330f), p(0.610f, 0.545f), p(0.535f, 0.610f), p(0.465f, 0.610f), p(0.390f, 0.545f))), 40),
        MuscleHitRegion(Muscle.QUADS, listOf(poly(p(0.305f, 0.560f), p(0.455f, 0.580f), p(0.440f, 0.790f), p(0.335f, 0.815f), p(0.275f, 0.690f)), poly(p(0.695f, 0.560f), p(0.545f, 0.580f), p(0.560f, 0.790f), p(0.665f, 0.815f), p(0.725f, 0.690f))), 58),
        MuscleHitRegion(Muscle.CALVES, listOf(poly(p(0.320f, 0.805f), p(0.430f, 0.810f), p(0.420f, 0.960f), p(0.345f, 0.970f), p(0.300f, 0.890f)), poly(p(0.680f, 0.805f), p(0.570f, 0.810f), p(0.580f, 0.960f), p(0.655f, 0.970f), p(0.700f, 0.890f))), 84)
    )
}

private fun maleBackRegions(): List<MuscleHitRegion> {
    return listOf(
        MuscleHitRegion(Muscle.TRAPS, listOf(poly(p(0.500f, 0.120f), p(0.655f, 0.255f), p(0.585f, 0.340f), p(0.500f, 0.330f), p(0.415f, 0.340f), p(0.345f, 0.255f))), 94),
        MuscleHitRegion(Muscle.SHOULDERS, listOf(oval(0.165f, 0.170f, 0.325f, 0.300f), oval(0.675f, 0.170f, 0.835f, 0.300f)), 90),
        MuscleHitRegion(Muscle.BACK, listOf(poly(p(0.280f, 0.225f), p(0.485f, 0.245f), p(0.465f, 0.495f), p(0.330f, 0.480f), p(0.235f, 0.350f)), poly(p(0.720f, 0.225f), p(0.515f, 0.245f), p(0.535f, 0.495f), p(0.670f, 0.480f), p(0.765f, 0.350f))), 48),
        MuscleHitRegion(Muscle.TRICEPS, listOf(oval(0.115f, 0.280f, 0.245f, 0.440f), oval(0.755f, 0.280f, 0.885f, 0.440f)), 86),
        MuscleHitRegion(Muscle.FOREARMS, listOf(poly(p(0.075f, 0.420f), p(0.185f, 0.415f), p(0.230f, 0.565f), p(0.135f, 0.595f), p(0.070f, 0.520f)), poly(p(0.925f, 0.420f), p(0.815f, 0.415f), p(0.770f, 0.565f), p(0.865f, 0.595f), p(0.930f, 0.520f))), 88),
        MuscleHitRegion(Muscle.GLUTES, listOf(oval(0.295f, 0.505f, 0.500f, 0.650f), oval(0.500f, 0.505f, 0.705f, 0.650f)), 72),
        MuscleHitRegion(Muscle.HAMSTRINGS, listOf(poly(p(0.315f, 0.645f), p(0.460f, 0.630f), p(0.445f, 0.835f), p(0.360f, 0.860f), p(0.300f, 0.745f)), poly(p(0.685f, 0.645f), p(0.540f, 0.630f), p(0.555f, 0.835f), p(0.640f, 0.860f), p(0.700f, 0.745f))), 62),
        MuscleHitRegion(Muscle.CALVES, listOf(oval(0.295f, 0.820f, 0.440f, 0.960f), oval(0.560f, 0.820f, 0.705f, 0.960f)), 84)
    )
}

private fun femaleFrontRegions(): List<MuscleHitRegion> {
    return listOf(
        MuscleHitRegion(Muscle.SHOULDERS, listOf(oval(0.155f, 0.165f, 0.315f, 0.275f), oval(0.685f, 0.165f, 0.845f, 0.275f)), 90),
        MuscleHitRegion(Muscle.CHEST, listOf(oval(0.300f, 0.235f, 0.495f, 0.360f), oval(0.505f, 0.235f, 0.700f, 0.360f)), 45),
        MuscleHitRegion(Muscle.BICEPS, listOf(oval(0.105f, 0.270f, 0.235f, 0.435f), oval(0.765f, 0.270f, 0.895f, 0.435f)), 85),
        MuscleHitRegion(Muscle.FOREARMS, listOf(poly(p(0.120f, 0.410f), p(0.220f, 0.390f), p(0.270f, 0.560f), p(0.180f, 0.600f), p(0.100f, 0.515f)), poly(p(0.880f, 0.410f), p(0.780f, 0.390f), p(0.730f, 0.560f), p(0.820f, 0.600f), p(0.900f, 0.515f))), 88),
        MuscleHitRegion(Muscle.ABS, listOf(poly(p(0.395f, 0.355f), p(0.605f, 0.355f), p(0.605f, 0.575f), p(0.525f, 0.635f), p(0.475f, 0.635f), p(0.395f, 0.575f))), 40),
        MuscleHitRegion(Muscle.QUADS, listOf(poly(p(0.285f, 0.580f), p(0.465f, 0.595f), p(0.445f, 0.800f), p(0.335f, 0.830f), p(0.260f, 0.705f)), poly(p(0.715f, 0.580f), p(0.535f, 0.595f), p(0.555f, 0.800f), p(0.665f, 0.830f), p(0.740f, 0.705f))), 58),
        MuscleHitRegion(Muscle.CALVES, listOf(poly(p(0.300f, 0.810f), p(0.425f, 0.810f), p(0.415f, 0.960f), p(0.345f, 0.975f), p(0.285f, 0.890f)), poly(p(0.700f, 0.810f), p(0.575f, 0.810f), p(0.585f, 0.960f), p(0.655f, 0.975f), p(0.715f, 0.890f))), 84)
    )
}

private fun femaleBackRegions(): List<MuscleHitRegion> {
    return listOf(
        MuscleHitRegion(Muscle.TRAPS, listOf(poly(p(0.500f, 0.115f), p(0.650f, 0.255f), p(0.585f, 0.340f), p(0.500f, 0.330f), p(0.415f, 0.340f), p(0.350f, 0.255f))), 94),
        MuscleHitRegion(Muscle.SHOULDERS, listOf(oval(0.155f, 0.185f, 0.315f, 0.305f), oval(0.685f, 0.185f, 0.845f, 0.305f)), 90),
        MuscleHitRegion(Muscle.BACK, listOf(poly(p(0.260f, 0.240f), p(0.485f, 0.250f), p(0.455f, 0.495f), p(0.315f, 0.485f), p(0.220f, 0.360f)), poly(p(0.740f, 0.240f), p(0.515f, 0.250f), p(0.545f, 0.495f), p(0.685f, 0.485f), p(0.780f, 0.360f))), 48),
        MuscleHitRegion(Muscle.TRICEPS, listOf(oval(0.100f, 0.295f, 0.235f, 0.455f), oval(0.765f, 0.295f, 0.900f, 0.455f)), 86),
        MuscleHitRegion(Muscle.FOREARMS, listOf(poly(p(0.060f, 0.435f), p(0.175f, 0.420f), p(0.225f, 0.575f), p(0.120f, 0.610f), p(0.055f, 0.530f)), poly(p(0.940f, 0.435f), p(0.825f, 0.420f), p(0.775f, 0.575f), p(0.880f, 0.610f), p(0.945f, 0.530f))), 88),
        MuscleHitRegion(Muscle.GLUTES, listOf(oval(0.270f, 0.505f, 0.505f, 0.665f), oval(0.495f, 0.505f, 0.730f, 0.665f)), 72),
        MuscleHitRegion(Muscle.HAMSTRINGS, listOf(poly(p(0.300f, 0.660f), p(0.470f, 0.635f), p(0.455f, 0.835f), p(0.365f, 0.860f), p(0.285f, 0.755f)), poly(p(0.700f, 0.660f), p(0.530f, 0.635f), p(0.545f, 0.835f), p(0.635f, 0.860f), p(0.715f, 0.755f))), 62),
        MuscleHitRegion(Muscle.CALVES, listOf(oval(0.295f, 0.825f, 0.445f, 0.965f), oval(0.555f, 0.825f, 0.705f, 0.965f)), 84)
    )
}

private fun oval(left: Float, top: Float, right: Float, bottom: Float): HitShape.Oval {
    val rect = Rect(left, top, right, bottom)
    val cx = rect.center.x
    val cy = rect.center.y
    val hw = (rect.width / 2) * 0.80f
    val hh = (rect.height / 2) * 0.80f
    return HitShape.Oval(Rect(cx - hw, cy - hh, cx + hw, cy + hh))
}

private fun poly(vararg points: Pair<Float, Float>): HitShape.Polygon {
    val offsets = points.map { Offset(it.first, it.second) }
    val cx = offsets.map { it.x }.average().toFloat()
    val cy = offsets.map { it.y }.average().toFloat()
    val shrunk = offsets.map { Offset(cx + (it.x - cx) * 0.80f, cy + (it.y - cy) * 0.80f) }
    return HitShape.Polygon(shrunk)
}

private fun p(x: Float, y: Float): Pair<Float, Float> = x to y

private fun Rect.scaleTo(child: Rect): Rect {
    return Rect(
        left = left + width * child.left,
        top = top + height * child.top,
        right = left + width * child.right,
        bottom = top + height * child.bottom
    )
}

private fun Offset.toCanvasPoint(bounds: Rect): Offset {
    return Offset(
        x = bounds.left + x * bounds.width,
        y = bounds.top + y * bounds.height
    )
}

private fun List<Offset>.toCanvasPath(bounds: Rect): Path {
    return Path().apply {
        forEachIndexed { index, point ->
            val canvasPoint = point.toCanvasPoint(bounds)
            if (index == 0) {
                moveTo(canvasPoint.x, canvasPoint.y)
            } else {
                lineTo(canvasPoint.x, canvasPoint.y)
            }
        }
        close()
    }
}

private fun polygonContains(point: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false

    var inside = false
    var previous = polygon.last()
    polygon.forEach { current ->
        val crossesY = (current.y > point.y) != (previous.y > point.y)
        if (crossesY) {
            val xIntersection = (previous.x - current.x) *
                (point.y - current.y) /
                ((previous.y - current.y).takeIf { it != 0f } ?: 0.0001f) +
                current.x
            if (point.x < xIntersection) inside = !inside
        }
        previous = current
    }
    return inside
}

private fun debugPaint(color: Color): Paint {
    return Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
        textSize = 22f
        strokeWidth = 1.5f
    }
}
