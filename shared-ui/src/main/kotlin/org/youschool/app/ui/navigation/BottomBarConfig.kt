package org.youschool.app.ui.navigation

import kotlin.math.roundToInt

object BottomBarConfig {
    val defaults = listOf("schedule", "grades", "homework", "profile")
    val available = defaults + "messages"

    fun tabIndex(x: Float, width: Float, padding: Float, count: Int): Int {
        if (count <= 0 || width <= padding * 2) return 0
        val ratio = ((x - padding) / (width - padding * 2)).coerceIn(0f, 0.999999f)
        return (ratio * count).toInt().coerceIn(0, count - 1)
    }

    fun nearestTabIndex(leftPx: Float, capsuleWidthPx: Float, count: Int): Int {
        if (count <= 0 || capsuleWidthPx <= 0f) return 0
        return (leftPx / capsuleWidthPx).roundToInt().coerceIn(0, count - 1)
    }

    fun calculatePillTargetLeft(
        isDownOnActivePill: Boolean,
        initialPillLeft: Float,
        pointerX: Float,
        startX: Float,
        paddingPx: Float,
        capsuleWidthPx: Float,
        innerWidthPx: Float
    ): Float {
        val maxLeft = (innerWidthPx - capsuleWidthPx).coerceAtLeast(0f)
        return if (isDownOnActivePill) {
            (initialPillLeft + (pointerX - startX)).coerceIn(0f, maxLeft)
        } else {
            (pointerX - paddingPx - capsuleWidthPx / 2f).coerceIn(0f, maxLeft)
        }
    }

    fun normalize(routes: List<String>): List<String> {
        val valid = routes.filter { it in available }.distinct().toMutableList()
        if ("profile" !in valid) valid.add("profile")
        return if (valid.size < 2) defaults else valid
    }
}
