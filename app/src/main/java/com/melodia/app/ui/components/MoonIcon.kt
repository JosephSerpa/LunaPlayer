package com.melodia.app.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Luna menguante minimalista: el mismo dibujo del icono de la app.
 * Se usa para "la mejor parte" de una canción y para el modo luna.
 */
val MoonIcon: ImageVector = materialIcon(name = "Melodia.Moon") {
    materialPath {
        moveTo(12f, 3f)
        arcToRelative(9f, 9f, 0f, true, false, 9f, 9f)
        curveToRelative(0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
        arcToRelative(5.389f, 5.389f, 0f, false, true, -4.4f, 2.26f)
        arcToRelative(5.403f, 5.403f, 0f, false, true, -3.14f, -9.8f)
        curveToRelative(-0.44f, -0.06f, -0.9f, -0.1f, -1.36f, -0.1f)
        close()
    }
}
