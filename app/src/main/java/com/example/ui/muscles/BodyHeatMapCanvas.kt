package com.example.ui.muscles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
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

@Composable
fun BodyHeatMapCanvas(
    bodyModel: BodyModel,
    heatmap: Map<Muscle, MuscleIntensity>,
    selected: Muscle?,
    onSelect: (Muscle?) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {


            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                AnatomyPanel(
                    label = "Frente",
                    isFront = true,
                    bodyModel = bodyModel,
                    heatmap = heatmap,
                    selected = selected,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f)
                )

                AnatomyPanel(
                    label = "Espalda",
                    isFront = false,
                    bodyModel = bodyModel,
                    heatmap = heatmap,
                    selected = selected,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f)
                )
            }


        }
    }
}

@Composable
private fun BodyModelTabs(
    bodyModel: BodyModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(50)
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModelTab(
            text = "Modelo masculino",
            selected = bodyModel == BodyModel.MALE,
            modifier = Modifier.weight(1f)
        )

        ModelTab(
            text = "Modelo femenino",
            selected = bodyModel == BodyModel.FEMALE,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModelTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            Color.Transparent
        },
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun AnatomyPanel(
    label: String,
    isFront: Boolean,
    bodyModel: BodyModel,
    heatmap: Map<Muscle, MuscleIntensity>,
    selected: Muscle?,
    onSelect: (Muscle?) -> Unit,
    modifier: Modifier = Modifier
) {
    val hitZones = remember(isFront, bodyModel) {
        if (isFront) frontHitZones() else backHitZones()
    }

    val imageRes = remember(isFront, bodyModel) {
        getBodyImageRes(
            bodyModel = bodyModel,
            isFront = isFront
        )
    }

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
                .aspectRatio(0.49f)
                .pointerInput(hitZones) {
                    detectTapGestures { tap ->
                        val hit = hitZones.lastOrNull { zone ->
                            zone.bounds(
                                width = size.width.toFloat(),
                                height = size.height.toFloat()
                            ).contains(tap)
                        }

                        onSelect(hit?.muscle?.let(Muscle::fromLegacyName))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                if (isFront) {
                    drawFrontHeatOverlay(
                        heatmap = heatmap,
                        selected = selected
                    )
                } else {
                    drawBackHeatOverlay(
                        heatmap = heatmap,
                        selected = selected
                    )
                }
            }
        }
    }
}

private fun getBodyImageRes(
    bodyModel: BodyModel,
    isFront: Boolean
): Int {
    return when {
        bodyModel == BodyModel.MALE && isFront -> R.drawable.body_male_front
        bodyModel == BodyModel.MALE && !isFront -> R.drawable.body_male_back
        bodyModel == BodyModel.FEMALE && isFront -> R.drawable.body_female_front
        else -> R.drawable.body_female_back
    }
}

private data class HitZone(
    val muscle: String,
    val nx: Float,
    val ny: Float,
    val nw: Float,
    val nh: Float
) {
    fun bounds(width: Float, height: Float): Rect {
        return Rect(
            left = nx * width,
            top = ny * height,
            right = (nx + nw) * width,
            bottom = (ny + nh) * height
        )
    }
}

private fun frontHitZones(): List<HitZone> {
    return listOf(
        HitZone("hombros", 0.23f, 0.16f, 0.14f, 0.08f),
        HitZone("hombros", 0.63f, 0.16f, 0.14f, 0.08f),
        HitZone("pecho", 0.34f, 0.20f, 0.32f, 0.10f),
        HitZone("biceps", 0.18f, 0.28f, 0.08f, 0.14f),
        HitZone("biceps", 0.74f, 0.28f, 0.08f, 0.14f),
        HitZone("antebrazos", 0.13f, 0.43f, 0.09f, 0.15f),
        HitZone("antebrazos", 0.78f, 0.43f, 0.09f, 0.15f),
        HitZone("abdomen", 0.41f, 0.32f, 0.18f, 0.20f),

        HitZone("cuadriceps", 0.33f, 0.57f, 0.13f, 0.22f),
        HitZone("cuadriceps", 0.54f, 0.57f, 0.13f, 0.22f),
        HitZone("aductores", 0.45f, 0.58f, 0.10f, 0.20f),
        HitZone("abductores", 0.28f, 0.56f, 0.07f, 0.20f),
        HitZone("abductores", 0.65f, 0.56f, 0.07f, 0.20f),
        HitZone("pantorrillas", 0.34f, 0.81f, 0.10f, 0.15f),
        HitZone("pantorrillas", 0.56f, 0.81f, 0.10f, 0.15f)
    )
}

private fun backHitZones(): List<HitZone> {
    return listOf(
        HitZone("hombros", 0.23f, 0.16f, 0.14f, 0.08f),
        HitZone("hombros", 0.63f, 0.16f, 0.14f, 0.08f),
        HitZone("espalda", 0.34f, 0.20f, 0.32f, 0.25f),
        // AnatomyPanel resolves overlaps with lastOrNull; keep traps after back
        // so the dedicated upper-back region remains independently selectable.
        HitZone("trapecio", 0.38f, 0.16f, 0.24f, 0.12f),
        HitZone("triceps", 0.18f, 0.28f, 0.08f, 0.14f),
        HitZone("triceps", 0.74f, 0.28f, 0.08f, 0.14f),
        HitZone("gluteos", 0.35f, 0.49f, 0.30f, 0.11f),

        HitZone("femorales", 0.34f, 0.62f, 0.12f, 0.20f),
        HitZone("femorales", 0.54f, 0.62f, 0.12f, 0.20f),
        HitZone("abductores", 0.28f, 0.56f, 0.07f, 0.20f),
        HitZone("abductores", 0.65f, 0.56f, 0.07f, 0.20f),
        HitZone("pantorrillas", 0.34f, 0.82f, 0.10f, 0.14f),
        HitZone("pantorrillas", 0.56f, 0.82f, 0.10f, 0.14f)
    )
}

private fun DrawScope.drawFrontHeatOverlay(
    heatmap: Map<Muscle, MuscleIntensity>,
    selected: Muscle?
) {
    val w = size.width
    val h = size.height

    drawMuscle("hombros", frontLeftShoulder(w, h), heatmap, selected)
    drawMuscle("hombros", frontRightShoulder(w, h), heatmap, selected)

    drawMuscle("pecho", frontLeftChest(w, h), heatmap, selected)
    drawMuscle("pecho", frontRightChest(w, h), heatmap, selected)

    drawMuscle("biceps", frontLeftBiceps(w, h), heatmap, selected)
    drawMuscle("biceps", frontRightBiceps(w, h), heatmap, selected)

    drawMuscle("antebrazos", frontLeftForearm(w, h), heatmap, selected)
    drawMuscle("antebrazos", frontRightForearm(w, h), heatmap, selected)

    drawMuscle("abdomen", frontAbs(w, h), heatmap, selected)

    drawMuscle("abductores", frontLeftAbductor(w, h), heatmap, selected)
    drawMuscle("abductores", frontRightAbductor(w, h), heatmap, selected)

    drawMuscle("aductores", frontAdductors(w, h), heatmap, selected)

    drawMuscle("cuadriceps", frontLeftQuad(w, h), heatmap, selected)
    drawMuscle("cuadriceps", frontRightQuad(w, h), heatmap, selected)

    drawMuscle("pantorrillas", frontLeftCalf(w, h), heatmap, selected)
    drawMuscle("pantorrillas", frontRightCalf(w, h), heatmap, selected)
}

private fun DrawScope.drawBackHeatOverlay(
    heatmap: Map<Muscle, MuscleIntensity>,
    selected: Muscle?
) {
    val w = size.width
    val h = size.height

    drawMuscle("hombros", backLeftShoulder(w, h), heatmap, selected)
    drawMuscle("hombros", backRightShoulder(w, h), heatmap, selected)

    drawMuscle("espalda", backLeftLat(w, h), heatmap, selected)
    drawMuscle("espalda", backRightLat(w, h), heatmap, selected)
    drawMuscle("trapecio", backTraps(w, h), heatmap, selected)

    drawMuscle("triceps", backLeftTriceps(w, h), heatmap, selected)
    drawMuscle("triceps", backRightTriceps(w, h), heatmap, selected)

    drawMuscle("gluteos", backLeftGlute(w, h), heatmap, selected)
    drawMuscle("gluteos", backRightGlute(w, h), heatmap, selected)

    drawMuscle("abductores", backLeftAbductor(w, h), heatmap, selected)
    drawMuscle("abductores", backRightAbductor(w, h), heatmap, selected)

    drawMuscle("femorales", backLeftHamstring(w, h), heatmap, selected)
    drawMuscle("femorales", backRightHamstring(w, h), heatmap, selected)

    drawMuscle("pantorrillas", backLeftCalf(w, h), heatmap, selected)
    drawMuscle("pantorrillas", backRightCalf(w, h), heatmap, selected)
}

private fun DrawScope.drawMuscle(
    muscle: String,
    path: Path,
    heatmap: Map<Muscle, MuscleIntensity>,
    selected: Muscle?
) {
    val muscleKey = Muscle.fromLegacyName(muscle)
    val intensity = muscleKey?.let { heatmap[it]?.intensity } ?: 0

    if (intensity > 0) {
        val color = Color(intensityToColor(intensity))

        drawPath(
            path = path,
            color = color.copy(alpha = intensityAlpha(intensity)),
            style = Fill
        )
    }

    if (selected != null && selected == muscleKey) {
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.95f),
            style = Stroke(width = 3.2f)
        )

        drawPath(
            path = path,
            color = Color(0xFF7C3AED).copy(alpha = 0.9f),
            style = Stroke(width = 1.4f)
        )
    }
}

private fun intensityAlpha(intensity: Int): Float {
    return when {
        intensity <= 0 -> 0.00f
        intensity <= 25 -> 0.22f
        intensity <= 50 -> 0.30f
        intensity <= 75 -> 0.38f
        else -> 0.46f
    }
}

/* FRONT PATHS */

private fun frontLeftShoulder(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.235f, h * 0.165f)
        cubicTo(w * 0.195f, h * 0.185f, w * 0.185f, h * 0.230f, w * 0.220f, h * 0.255f)
        cubicTo(w * 0.270f, h * 0.265f, w * 0.320f, h * 0.230f, w * 0.330f, h * 0.190f)
        cubicTo(w * 0.300f, h * 0.170f, w * 0.265f, h * 0.160f, w * 0.235f, h * 0.165f)
        close()
    }
}

private fun frontRightShoulder(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.765f, h * 0.165f)
        cubicTo(w * 0.805f, h * 0.185f, w * 0.815f, h * 0.230f, w * 0.780f, h * 0.255f)
        cubicTo(w * 0.730f, h * 0.265f, w * 0.680f, h * 0.230f, w * 0.670f, h * 0.190f)
        cubicTo(w * 0.700f, h * 0.170f, w * 0.735f, h * 0.160f, w * 0.765f, h * 0.165f)
        close()
    }
}

private fun frontLeftChest(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.50f, h * 0.205f)
        cubicTo(w * 0.405f, h * 0.195f, w * 0.330f, h * 0.220f, w * 0.315f, h * 0.285f)
        cubicTo(w * 0.340f, h * 0.325f, w * 0.430f, h * 0.330f, w * 0.500f, h * 0.300f)
        close()
    }
}

private fun frontRightChest(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.50f, h * 0.205f)
        cubicTo(w * 0.595f, h * 0.195f, w * 0.670f, h * 0.220f, w * 0.685f, h * 0.285f)
        cubicTo(w * 0.660f, h * 0.325f, w * 0.570f, h * 0.330f, w * 0.500f, h * 0.300f)
        close()
    }
}

private fun frontLeftBiceps(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.180f, h * 0.270f)
        cubicTo(w * 0.145f, h * 0.330f, w * 0.155f, h * 0.405f, w * 0.200f, h * 0.450f)
        cubicTo(w * 0.245f, h * 0.410f, w * 0.255f, h * 0.320f, w * 0.225f, h * 0.270f)
        close()
    }
}

private fun frontRightBiceps(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.820f, h * 0.270f)
        cubicTo(w * 0.855f, h * 0.330f, w * 0.845f, h * 0.405f, w * 0.800f, h * 0.450f)
        cubicTo(w * 0.755f, h * 0.410f, w * 0.745f, h * 0.320f, w * 0.775f, h * 0.270f)
        close()
    }
}

private fun frontLeftForearm(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.190f, h * 0.440f)
        cubicTo(w * 0.130f, h * 0.500f, w * 0.135f, h * 0.565f, w * 0.165f, h * 0.620f)
        cubicTo(w * 0.220f, h * 0.580f, w * 0.235f, h * 0.500f, w * 0.215f, h * 0.440f)
        close()
    }
}

private fun frontRightForearm(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.810f, h * 0.440f)
        cubicTo(w * 0.870f, h * 0.500f, w * 0.865f, h * 0.565f, w * 0.835f, h * 0.620f)
        cubicTo(w * 0.780f, h * 0.580f, w * 0.765f, h * 0.500f, w * 0.785f, h * 0.440f)
        close()
    }
}

private fun frontAbs(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.400f, h * 0.315f)
        cubicTo(w * 0.365f, h * 0.380f, w * 0.375f, h * 0.485f, w * 0.420f, h * 0.535f)
        cubicTo(w * 0.470f, h * 0.555f, w * 0.530f, h * 0.555f, w * 0.580f, h * 0.535f)
        cubicTo(w * 0.625f, h * 0.485f, w * 0.635f, h * 0.380f, w * 0.600f, h * 0.315f)
        cubicTo(w * 0.545f, h * 0.330f, w * 0.455f, h * 0.330f, w * 0.400f, h * 0.315f)
        close()
    }
}

private fun frontLeftQuad(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.330f, h * 0.555f)
        cubicTo(w * 0.290f, h * 0.635f, w * 0.300f, h * 0.750f, w * 0.350f, h * 0.815f)
        cubicTo(w * 0.410f, h * 0.805f, w * 0.450f, h * 0.735f, w * 0.445f, h * 0.610f)
        cubicTo(w * 0.415f, h * 0.570f, w * 0.370f, h * 0.550f, w * 0.330f, h * 0.555f)
        close()
    }
}

private fun frontRightQuad(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.670f, h * 0.555f)
        cubicTo(w * 0.710f, h * 0.635f, w * 0.700f, h * 0.750f, w * 0.650f, h * 0.815f)
        cubicTo(w * 0.590f, h * 0.805f, w * 0.550f, h * 0.735f, w * 0.555f, h * 0.610f)
        cubicTo(w * 0.585f, h * 0.570f, w * 0.630f, h * 0.550f, w * 0.670f, h * 0.555f)
        close()
    }
}

private fun frontAdductors(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.430f, h * 0.565f)
        cubicTo(w * 0.410f, h * 0.650f, w * 0.430f, h * 0.755f, w * 0.490f, h * 0.815f)
        cubicTo(w * 0.505f, h * 0.730f, w * 0.505f, h * 0.620f, w * 0.500f, h * 0.565f)
        cubicTo(w * 0.495f, h * 0.620f, w * 0.495f, h * 0.730f, w * 0.510f, h * 0.815f)
        cubicTo(w * 0.570f, h * 0.755f, w * 0.590f, h * 0.650f, w * 0.570f, h * 0.565f)
        close()
    }
}

private fun frontLeftAbductor(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.285f, h * 0.555f)
        cubicTo(w * 0.245f, h * 0.620f, w * 0.250f, h * 0.720f, w * 0.295f, h * 0.790f)
        cubicTo(w * 0.320f, h * 0.715f, w * 0.325f, h * 0.625f, w * 0.325f, h * 0.565f)
        close()
    }
}

private fun frontRightAbductor(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.715f, h * 0.555f)
        cubicTo(w * 0.755f, h * 0.620f, w * 0.750f, h * 0.720f, w * 0.705f, h * 0.790f)
        cubicTo(w * 0.680f, h * 0.715f, w * 0.675f, h * 0.625f, w * 0.675f, h * 0.565f)
        close()
    }
}

private fun frontLeftCalf(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.335f, h * 0.815f)
        cubicTo(w * 0.285f, h * 0.875f, w * 0.305f, h * 0.955f, w * 0.365f, h * 0.980f)
        cubicTo(w * 0.420f, h * 0.940f, w * 0.425f, h * 0.860f, w * 0.390f, h * 0.815f)
        close()
    }
}

private fun frontRightCalf(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.665f, h * 0.815f)
        cubicTo(w * 0.715f, h * 0.875f, w * 0.695f, h * 0.955f, w * 0.635f, h * 0.980f)
        cubicTo(w * 0.580f, h * 0.940f, w * 0.575f, h * 0.860f, w * 0.610f, h * 0.815f)
        close()
    }
}

/* BACK PATHS */

private fun backLeftShoulder(w: Float, h: Float): Path = frontLeftShoulder(w, h)

private fun backRightShoulder(w: Float, h: Float): Path = frontRightShoulder(w, h)

private fun backTraps(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.500f, h * 0.160f)
        cubicTo(w * 0.415f, h * 0.185f, w * 0.390f, h * 0.260f, w * 0.420f, h * 0.350f)
        cubicTo(w * 0.465f, h * 0.365f, w * 0.535f, h * 0.365f, w * 0.580f, h * 0.350f)
        cubicTo(w * 0.610f, h * 0.260f, w * 0.585f, h * 0.185f, w * 0.500f, h * 0.160f)
        close()
    }
}

private fun backLeftLat(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.330f, h * 0.205f)
        cubicTo(w * 0.270f, h * 0.285f, w * 0.270f, h * 0.395f, w * 0.360f, h * 0.500f)
        cubicTo(w * 0.430f, h * 0.470f, w * 0.470f, h * 0.380f, w * 0.480f, h * 0.275f)
        cubicTo(w * 0.430f, h * 0.225f, w * 0.380f, h * 0.205f, w * 0.330f, h * 0.205f)
        close()
    }
}

private fun backRightLat(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.670f, h * 0.205f)
        cubicTo(w * 0.730f, h * 0.285f, w * 0.730f, h * 0.395f, w * 0.640f, h * 0.500f)
        cubicTo(w * 0.570f, h * 0.470f, w * 0.530f, h * 0.380f, w * 0.520f, h * 0.275f)
        cubicTo(w * 0.570f, h * 0.225f, w * 0.620f, h * 0.205f, w * 0.670f, h * 0.205f)
        close()
    }
}

private fun backLeftTriceps(w: Float, h: Float): Path {
    return Path().apply {
        // Posterior arm — triceps sits on the outer/back side, wider at top
        moveTo(w * 0.195f, h * 0.270f)
        cubicTo(w * 0.160f, h * 0.320f, w * 0.150f, h * 0.390f, w * 0.185f, h * 0.445f)
        cubicTo(w * 0.215f, h * 0.420f, w * 0.240f, h * 0.350f, w * 0.240f, h * 0.290f)
        cubicTo(w * 0.225f, h * 0.265f, w * 0.210f, h * 0.260f, w * 0.195f, h * 0.270f)
        close()
    }
}

private fun backRightTriceps(w: Float, h: Float): Path {
    return Path().apply {
        // Mirror of left triceps
        moveTo(w * 0.805f, h * 0.270f)
        cubicTo(w * 0.840f, h * 0.320f, w * 0.850f, h * 0.390f, w * 0.815f, h * 0.445f)
        cubicTo(w * 0.785f, h * 0.420f, w * 0.760f, h * 0.350f, w * 0.760f, h * 0.290f)
        cubicTo(w * 0.775f, h * 0.265f, w * 0.790f, h * 0.260f, w * 0.805f, h * 0.270f)
        close()
    }
}

private fun backLeftGlute(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.325f, h * 0.490f)
        cubicTo(w * 0.285f, h * 0.540f, w * 0.315f, h * 0.615f, w * 0.490f, h * 0.610f)
        cubicTo(w * 0.500f, h * 0.555f, w * 0.455f, h * 0.500f, w * 0.325f, h * 0.490f)
        close()
    }
}

private fun backRightGlute(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.675f, h * 0.490f)
        cubicTo(w * 0.715f, h * 0.540f, w * 0.685f, h * 0.615f, w * 0.510f, h * 0.610f)
        cubicTo(w * 0.500f, h * 0.555f, w * 0.545f, h * 0.500f, w * 0.675f, h * 0.490f)
        close()
    }
}

private fun backLeftHamstring(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.335f, h * 0.610f)
        cubicTo(w * 0.300f, h * 0.680f, w * 0.310f, h * 0.780f, w * 0.355f, h * 0.845f)
        cubicTo(w * 0.410f, h * 0.825f, w * 0.440f, h * 0.740f, w * 0.430f, h * 0.625f)
        cubicTo(w * 0.400f, h * 0.610f, w * 0.365f, h * 0.605f, w * 0.335f, h * 0.610f)
        close()
    }
}

private fun backRightHamstring(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.665f, h * 0.610f)
        cubicTo(w * 0.700f, h * 0.680f, w * 0.690f, h * 0.780f, w * 0.645f, h * 0.845f)
        cubicTo(w * 0.590f, h * 0.825f, w * 0.560f, h * 0.740f, w * 0.570f, h * 0.625f)
        cubicTo(w * 0.600f, h * 0.610f, w * 0.635f, h * 0.605f, w * 0.665f, h * 0.610f)
        close()
    }
}

private fun backLeftAbductor(w: Float, h: Float): Path = frontLeftAbductor(w, h)

private fun backRightAbductor(w: Float, h: Float): Path = frontRightAbductor(w, h)

private fun backLeftCalf(w: Float, h: Float): Path = frontLeftCalf(w, h)

private fun backRightCalf(w: Float, h: Float): Path = frontRightCalf(w, h)

/* LEGEND */

@Composable
private fun HeatMapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem("Sin datos", Color(0xFF334155))
        LegendItem("Baja", Color(0xFFEAB308))
        LegendItem("Media", Color(0xFFFB923C))
        LegendItem("Alta", Color(0xFFF97316))
        LegendItem("Max", Color(0xFFE11D48))
    }
}

@Composable
private fun LegendItem(
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    color = color,
                    shape = CircleShape
                )
        )

        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
