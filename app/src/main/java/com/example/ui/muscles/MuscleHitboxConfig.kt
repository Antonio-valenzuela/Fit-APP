package com.example.ui.muscles

import com.example.data.model.BodyModel
import com.example.data.model.Muscle

data class ConfigPoint(val x: Float, val y: Float)

data class ConfigRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(point: ConfigPoint): Boolean {
        return point.x >= left && point.x <= right && point.y >= top && point.y <= bottom
    }
}

enum class ConfigShapeType {
    OVAL,
    POLYGON
}

enum class BodyView {
    FRONT,
    BACK
}

data class ConfigShape(
    val type: ConfigShapeType,
    val points: List<ConfigPoint>
) {
    fun contains(point: ConfigPoint): Boolean {
        return when (type) {
            ConfigShapeType.OVAL -> {
                if (points.size < 2) return false
                val rectLeft = points[0].x
                val rectTop = points[0].y
                val rectRight = points[1].x
                val rectBottom = points[1].y
                val rx = (rectRight - rectLeft) / 2f
                val ry = (rectBottom - rectTop) / 2f
                if (rx <= 0f || ry <= 0f) return false

                val cx = rectLeft + rx
                val cy = rectTop + ry
                val dx = Math.pow(((point.x - cx) / rx).toDouble(), 2.0)
                val dy = Math.pow(((point.y - cy) / ry).toDouble(), 2.0)
                dx + dy <= 1.0
            }
            ConfigShapeType.POLYGON -> {
                if (points.size < 3) return false
                var inside = false
                var previous = points.last()
                points.forEach { current ->
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
                inside
            }
        }
    }
}

data class ConfigRegion(
    val muscle: Muscle,
    val shapes: List<ConfigShape>,
    val priority: Int = 0
) {
    fun contains(point: ConfigPoint): Boolean = shapes.any { it.contains(point) }
}

data class MuscleHitboxViewConfig(
    val bodyModel: BodyModel,
    val bodyView: BodyView,
    val imageAspectRatio: Float,
    val crop: ConfigRect,
    val regions: List<ConfigRegion>
)

object MuscleHitboxRegistry {
    private val configurations: Map<Pair<BodyModel, BodyView>, MuscleHitboxViewConfig>

    init {
        configurations = mapOf(
            (BodyModel.MALE to BodyView.FRONT) to MuscleHitboxViewConfig(
                bodyModel = BodyModel.MALE,
                bodyView = BodyView.FRONT,
                imageAspectRatio = 682f / 1387f,
                crop = ConfigRect(0.01f, 0.00f, 0.99f, 0.995f),
                regions = maleFrontRegions()
            ),
            (BodyModel.MALE to BodyView.BACK) to MuscleHitboxViewConfig(
                bodyModel = BodyModel.MALE,
                bodyView = BodyView.BACK,
                imageAspectRatio = 647f / 1386f,
                crop = ConfigRect(0.02f, 0.00f, 0.98f, 0.995f),
                regions = maleBackRegions()
            ),
            (BodyModel.FEMALE to BodyView.FRONT) to MuscleHitboxViewConfig(
                bodyModel = BodyModel.FEMALE,
                bodyView = BodyView.FRONT,
                imageAspectRatio = 634f / 1370f,
                crop = ConfigRect(0.02f, 0.00f, 0.98f, 0.995f),
                regions = femaleFrontRegions()
            ),
            (BodyModel.FEMALE to BodyView.BACK) to MuscleHitboxViewConfig(
                bodyModel = BodyModel.FEMALE,
                bodyView = BodyView.BACK,
                imageAspectRatio = 613f / 1367f,
                crop = ConfigRect(0.01f, 0.00f, 0.99f, 0.995f),
                regions = femaleBackRegions()
            )
        )
    }

    fun getConfig(model: BodyModel, view: BodyView): MuscleHitboxViewConfig {
        return configurations[model to view] ?: throw IllegalArgumentException("No config for $model $view")
    }

    private fun oval(left: Float, top: Float, right: Float, bottom: Float): ConfigShape {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val hw = ((right - left) / 2f) * 0.80f
        val hh = ((bottom - top) / 2f) * 0.80f
        return ConfigShape(
            type = ConfigShapeType.OVAL,
            points = listOf(ConfigPoint(cx - hw, cy - hh), ConfigPoint(cx + hw, cy + hh))
        )
    }

    private fun poly(vararg points: Pair<Float, Float>): ConfigShape {
        val offsets = points.map { ConfigPoint(it.first, it.second) }
        val cx = offsets.map { it.x }.average().toFloat()
        val cy = offsets.map { it.y }.average().toFloat()
        val shrunk = offsets.map { ConfigPoint(cx + (it.x - cx) * 0.80f, cy + (it.y - cy) * 0.80f) }
        return ConfigShape(
            type = ConfigShapeType.POLYGON,
            points = shrunk
        )
    }

    private fun p(x: Float, y: Float): Pair<Float, Float> = x to y

    private fun maleFrontRegions(): List<ConfigRegion> {
        return listOf(
            ConfigRegion(Muscle.SHOULDERS, listOf(oval(0.17f, 0.155f, 0.315f, 0.270f), oval(0.685f, 0.155f, 0.830f, 0.270f)), 90),
            ConfigRegion(Muscle.CHEST, listOf(poly(p(0.315f, 0.215f), p(0.500f, 0.195f), p(0.498f, 0.330f), p(0.355f, 0.350f), p(0.285f, 0.300f)), poly(p(0.685f, 0.215f), p(0.500f, 0.195f), p(0.502f, 0.330f), p(0.645f, 0.350f), p(0.715f, 0.300f))), 45),
            ConfigRegion(Muscle.BICEPS, listOf(oval(0.120f, 0.250f, 0.250f, 0.425f), oval(0.750f, 0.250f, 0.880f, 0.425f)), 85),
            ConfigRegion(Muscle.FOREARMS, listOf(poly(p(0.140f, 0.385f), p(0.235f, 0.360f), p(0.285f, 0.520f), p(0.200f, 0.560f), p(0.125f, 0.495f)), poly(p(0.860f, 0.385f), p(0.765f, 0.360f), p(0.715f, 0.520f), p(0.800f, 0.560f), p(0.875f, 0.495f))), 88),
            ConfigRegion(Muscle.ABS, listOf(poly(p(0.405f, 0.330f), p(0.595f, 0.330f), p(0.610f, 0.545f), p(0.535f, 0.610f), p(0.465f, 0.610f), p(0.390f, 0.545f))), 40),
            ConfigRegion(Muscle.QUADS, listOf(poly(p(0.305f, 0.560f), p(0.455f, 0.580f), p(0.440f, 0.790f), p(0.335f, 0.815f), p(0.275f, 0.690f)), poly(p(0.695f, 0.560f), p(0.545f, 0.580f), p(0.560f, 0.790f), p(0.665f, 0.815f), p(0.725f, 0.690f))), 58),
            ConfigRegion(Muscle.CALVES, listOf(poly(p(0.320f, 0.805f), p(0.430f, 0.810f), p(0.420f, 0.960f), p(0.345f, 0.970f), p(0.300f, 0.890f)), poly(p(0.680f, 0.805f), p(0.570f, 0.810f), p(0.580f, 0.960f), p(0.655f, 0.970f), p(0.700f, 0.890f))), 84)
        )
    }

    private fun maleBackRegions(): List<ConfigRegion> {
        return listOf(
            ConfigRegion(Muscle.TRAPS, listOf(poly(p(0.500f, 0.120f), p(0.655f, 0.255f), p(0.585f, 0.340f), p(0.500f, 0.330f), p(0.415f, 0.340f), p(0.345f, 0.255f))), 94),
            ConfigRegion(Muscle.SHOULDERS, listOf(oval(0.165f, 0.170f, 0.325f, 0.300f), oval(0.675f, 0.170f, 0.835f, 0.300f)), 90),
            ConfigRegion(Muscle.BACK, listOf(poly(p(0.280f, 0.225f), p(0.485f, 0.245f), p(0.465f, 0.495f), p(0.330f, 0.480f), p(0.235f, 0.350f)), poly(p(0.720f, 0.225f), p(0.515f, 0.245f), p(0.535f, 0.495f), p(0.670f, 0.480f), p(0.765f, 0.350f))), 48),
            ConfigRegion(Muscle.TRICEPS, listOf(oval(0.115f, 0.280f, 0.245f, 0.440f), oval(0.755f, 0.280f, 0.885f, 0.440f)), 86),
            ConfigRegion(Muscle.FOREARMS, listOf(poly(p(0.075f, 0.420f), p(0.185f, 0.415f), p(0.230f, 0.565f), p(0.135f, 0.595f), p(0.070f, 0.520f)), poly(p(0.925f, 0.420f), p(0.815f, 0.415f), p(0.770f, 0.565f), p(0.865f, 0.595f), p(0.930f, 0.520f))), 88),
            ConfigRegion(Muscle.GLUTES, listOf(oval(0.295f, 0.505f, 0.500f, 0.650f), oval(0.500f, 0.505f, 0.705f, 0.650f)), 72),
            ConfigRegion(Muscle.HAMSTRINGS, listOf(poly(p(0.315f, 0.645f), p(0.460f, 0.630f), p(0.445f, 0.835f), p(0.360f, 0.860f), p(0.300f, 0.745f)), poly(p(0.685f, 0.645f), p(0.540f, 0.630f), p(0.555f, 0.835f), p(0.640f, 0.860f), p(0.700f, 0.745f))), 62),
            ConfigRegion(Muscle.CALVES, listOf(oval(0.295f, 0.820f, 0.440f, 0.960f), oval(0.560f, 0.820f, 0.705f, 0.960f)), 84)
        )
    }

    private fun femaleFrontRegions(): List<ConfigRegion> {
        return listOf(
            ConfigRegion(Muscle.SHOULDERS, listOf(oval(0.155f, 0.165f, 0.315f, 0.275f), oval(0.685f, 0.165f, 0.845f, 0.275f)), 90),
            ConfigRegion(Muscle.CHEST, listOf(oval(0.300f, 0.235f, 0.495f, 0.360f), oval(0.505f, 0.235f, 0.700f, 0.360f)), 45),
            ConfigRegion(Muscle.BICEPS, listOf(oval(0.105f, 0.270f, 0.235f, 0.435f), oval(0.765f, 0.270f, 0.895f, 0.435f)), 85),
            ConfigRegion(Muscle.FOREARMS, listOf(poly(p(0.120f, 0.410f), p(0.220f, 0.390f), p(0.270f, 0.560f), p(0.180f, 0.600f), p(0.100f, 0.515f)), poly(p(0.880f, 0.410f), p(0.780f, 0.390f), p(0.730f, 0.560f), p(0.820f, 0.600f), p(0.900f, 0.515f))), 88),
            ConfigRegion(Muscle.ABS, listOf(poly(p(0.395f, 0.355f), p(0.605f, 0.355f), p(0.605f, 0.575f), p(0.525f, 0.635f), p(0.475f, 0.635f), p(0.395f, 0.575f))), 40),
            ConfigRegion(Muscle.QUADS, listOf(poly(p(0.285f, 0.580f), p(0.465f, 0.595f), p(0.445f, 0.800f), p(0.335f, 0.830f), p(0.260f, 0.705f)), poly(p(0.715f, 0.580f), p(0.535f, 0.595f), p(0.555f, 0.800f), p(0.665f, 0.830f), p(0.740f, 0.705f))), 58),
            ConfigRegion(Muscle.CALVES, listOf(poly(p(0.300f, 0.810f), p(0.425f, 0.810f), p(0.415f, 0.960f), p(0.345f, 0.975f), p(0.285f, 0.890f)), poly(p(0.700f, 0.810f), p(0.575f, 0.810f), p(0.585f, 0.960f), p(0.655f, 0.975f), p(0.715f, 0.890f))), 84)
        )
    }

    private fun femaleBackRegions(): List<ConfigRegion> {
        return listOf(
            ConfigRegion(Muscle.TRAPS, listOf(poly(p(0.500f, 0.115f), p(0.650f, 0.255f), p(0.585f, 0.340f), p(0.500f, 0.330f), p(0.415f, 0.340f), p(0.350f, 0.255f))), 94),
            ConfigRegion(Muscle.SHOULDERS, listOf(oval(0.155f, 0.185f, 0.315f, 0.305f), oval(0.685f, 0.185f, 0.845f, 0.305f)), 90),
            ConfigRegion(Muscle.BACK, listOf(poly(p(0.260f, 0.240f), p(0.485f, 0.250f), p(0.455f, 0.495f), p(0.315f, 0.485f), p(0.220f, 0.360f)), poly(p(0.740f, 0.240f), p(0.515f, 0.250f), p(0.545f, 0.495f), p(0.685f, 0.485f), p(0.780f, 0.360f))), 48),
            ConfigRegion(Muscle.TRICEPS, listOf(oval(0.100f, 0.295f, 0.235f, 0.455f), oval(0.765f, 0.295f, 0.900f, 0.455f)), 86),
            ConfigRegion(Muscle.FOREARMS, listOf(poly(p(0.060f, 0.435f), p(0.175f, 0.420f), p(0.225f, 0.575f), p(0.120f, 0.610f), p(0.055f, 0.530f)), poly(p(0.940f, 0.435f), p(0.825f, 0.420f), p(0.775f, 0.575f), p(0.880f, 0.610f), p(0.945f, 0.530f))), 88),
            ConfigRegion(Muscle.GLUTES, listOf(oval(0.270f, 0.505f, 0.505f, 0.665f), oval(0.495f, 0.505f, 0.730f, 0.665f)), 72),
            ConfigRegion(Muscle.HAMSTRINGS, listOf(poly(p(0.300f, 0.660f), p(0.470f, 0.635f), p(0.455f, 0.835f), p(0.365f, 0.860f), p(0.285f, 0.755f)), poly(p(0.700f, 0.660f), p(0.530f, 0.635f), p(0.545f, 0.835f), p(0.635f, 0.860f), p(0.715f, 0.755f))), 62),
            ConfigRegion(Muscle.CALVES, listOf(oval(0.295f, 0.825f, 0.445f, 0.965f), oval(0.555f, 0.825f, 0.705f, 0.965f)), 84)
        )
    }
}
