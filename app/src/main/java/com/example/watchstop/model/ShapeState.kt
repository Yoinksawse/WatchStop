package com.example.watchstop.model

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

class ShapeState(
    var x: Float,
    var y: Float,
    size: Float,
    alpha: Float,
    val color: Color
) {
    val sizeAnim = Animatable(size)
    val alphaAnim = Animatable(alpha)
}