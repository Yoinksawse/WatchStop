package com.example.watchstop.view


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

private var _locationAlarm: ImageVector? = null
public val Icons.Filled.LocationAlarm: ImageVector
    get() {
        if (_locationAlarm != null) {
            return _locationAlarm!!
        }
        _locationAlarm = materialIcon(name = "Filled.LocationAlarm") {
            // --- 1. SIDE ARCS (PUSHED WIDER OUTWARD) ---
            materialPath {
                // LEFT ARC: Pushed out to x=1.0
                moveTo(1.0f, 14f)
                curveToRelative(0f, -1.8f, 1f, -3.5f, 3f, -4.5f)
                lineTo(4.5f, 11f)
                curveToRelative(-1.5f, 0.8f, -2f, 1.8f, -2f, 3f)
                curveToRelative(0f, 2.5f, 3f, 4.2f, 6f, 4.2f)
                lineTo(8.5f, 18.2f) // Meets widened trench at 8.5
                verticalLineToRelative(0.8f)
                curveToRelative(-4.5f, 0f, -7.5f, -2.2f, -7.5f, -5f)
                close()

                // RIGHT ARC: Pushed out to x=23.0
                moveTo(23.0f, 14f)
                curveToRelative(0f, -1.8f, -1f, -3.5f, -3f, -4.5f)
                lineTo(19.5f, 11f)
                curveToRelative(1.5f, 0.8f, 2f, 1.8f, 2f, 3f)
                curveToRelative(0f, 2.5f, -3f, 4.2f, -6f, 4.2f)
                lineTo(15.5f, 18.2f) // Meets widened trench at 15.5
                verticalLineToRelative(0.8f)
                curveToRelative(4.5f, 0f, 7.5f, -2.2f, 7.5f, -5f)
                close()
            }

            materialPath(pathFillType = PathFillType.EvenOdd) {
                // --- 2. THE PIN ---
                moveTo(12f, 1.2f)
                curveTo(7.7f, 1.2f, 4.2f, 4.7f, 4.2f, 9f)
                curveToRelative(0f, 5.8f, 7.8f, 14.5f, 7.8f, 14.5f)
                reflectiveCurveToRelative(7.8f, -8.7f, 7.8f, -14.5f)
                curveToRelative(0f, -4.3f, -3.5f, -7.8f, -7.8f, -7.8f)
                close()

                // --- 3. REFINED BELL ---
                moveTo(12f, 4.7f)
                arcToRelative(2.2f, 2.2f, 0f, false, false, -2.2f, 2.2f)
                verticalLineToRelative(3.1f)
                lineToRelative(-1.1f, 1.1f)
                verticalLineToRelative(1.1f)
                horizontalLineToRelative(6.6f)
                verticalLineToRelative(-1.1f)
                lineToRelative(-1.1f, -1.1f)
                verticalLineToRelative(-3.1f)
                arcToRelative(2.2f, 2.2f, 0f, false, false, -2.2f, -2.2f)
                close()

                // --- 4. REFINED CLAPPER ---
                moveTo(10.9f, 13.2f)
                arcToRelative(1.1f, 1.1f, 0f, false, false, 2.2f, 0f)
                close()

                // --- 5. REFINED RINGING LINES ---
                moveTo(7.9f, 7.0f)
                lineTo(7.3f, 9.1f)
                lineTo(7.9f, 9.3f)
                lineTo(8.5f, 7.2f)
                close()

                moveTo(16.1f, 7.0f)
                lineTo(16.7f, 9.1f)
                lineTo(16.1f, 9.3f)
                lineTo(15.5f, 7.2f)
                close()

                // --- 6. THE WIDER TRENCH ---
                // Widened span from [8.5 to 15.5] to bridge the wide halos
                moveTo(8.5f, 18.5f)
                curveToRelative(1.2f, 0.05f, 2.3f, 0.1f, 3.5f, 0.1f)
                reflectiveCurveToRelative(2.3f, -0.05f, 3.5f, -0.1f)
                lineToRelative(0.0f, 0.5f)
                curveToRelative(-1.2f, 0.1f, -2.3f, 0.15f, -3.5f, 0.15f)
                reflectiveCurveToRelative(-2.3f, -0.05f, -3.5f, -0.15f)
                close()
            }
        }
        return _locationAlarm!!
    }