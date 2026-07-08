package com.example.ui.muscles

data class HitboxPoint(val x: Float, val y: Float)

data class HitboxRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(point: HitboxPoint): Boolean {
        return point.x >= left && point.x <= right && point.y >= top && point.y <= bottom
    }
}

object HitboxTransformer {
    fun calculateImageDrawBounds(
        containerWidth: Float,
        containerHeight: Float,
        imageAspectRatio: Float
    ): HitboxRect {
        if (containerHeight <= 0f || containerWidth <= 0f || imageAspectRatio <= 0f) {
            return HitboxRect(0f, 0f, 0f, 0f)
        }
        val containerAspect = containerWidth / containerHeight
        val imageDrawWidth: Float
        val imageDrawHeight: Float

        if (containerAspect > imageAspectRatio) {
            imageDrawHeight = containerHeight
            imageDrawWidth = imageDrawHeight * imageAspectRatio
        } else {
            imageDrawWidth = containerWidth
            imageDrawHeight = imageDrawWidth / imageAspectRatio
        }

        val imageOffsetX = (containerWidth - imageDrawWidth) / 2f
        val imageOffsetY = (containerHeight - imageDrawHeight) / 2f
        return HitboxRect(
            left = imageOffsetX,
            top = imageOffsetY,
            right = imageOffsetX + imageDrawWidth,
            bottom = imageOffsetY + imageDrawHeight
        )
    }

    fun transformCanvasToNormalized(
        tapX: Float,
        tapY: Float,
        containerWidth: Float,
        containerHeight: Float,
        imageAspectRatio: Float
    ): HitboxPoint? {
        val bounds = calculateImageDrawBounds(containerWidth, containerHeight, imageAspectRatio)
        val tap = HitboxPoint(tapX, tapY)
        if (!bounds.contains(tap)) {
            return null
        }
        val xNorm = (tapX - bounds.left) / bounds.width
        val yNorm = (tapY - bounds.top) / bounds.height
        return HitboxPoint(xNorm, yNorm)
    }
}
