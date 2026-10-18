package com.melodia.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

const val KOFI_URL = "https://ko-fi.com/raizzon"

/** Color de marca de Ko-fi. */
val KofiColor = Color(0xFFFF5E5B)

/** Abre la página de Ko-fi en el navegador. */
fun openKofi(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Taza de café con un corazón (el corazón es un hueco: toma el color del fondo). */
val KofiIcon: ImageVector = ImageVector.Builder(
    name = "Melodia.Kofi", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).apply {
    // Taza con el corazón recortado.
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        moveTo(2.5f, 6.5f); horizontalLineTo(18f); verticalLineTo(13f)
        arcTo(6f, 6f, 0f, false, true, 12f, 19f); horizontalLineTo(8.5f)
        arcTo(6f, 6f, 0f, false, true, 2.5f, 13f); close()
        // Corazón.
        moveTo(10.2f, 16.4f); lineTo(6.9f, 13.1f)
        arcTo(2f, 2f, 0f, false, true, 9.7f, 10.3f); lineTo(10.2f, 10.8f)
        lineTo(10.7f, 10.3f)
        arcTo(2f, 2f, 0f, false, true, 13.5f, 13.1f); close()
    }
    // Asa.
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        moveTo(18f, 8.5f); horizontalLineTo(19f)
        arcTo(3.2f, 3.2f, 0f, false, true, 19f, 14.9f); horizontalLineTo(18f)
        verticalLineTo(13.2f); horizontalLineTo(19f)
        arcTo(1.5f, 1.5f, 0f, false, false, 19f, 10.2f); horizontalLineTo(18f); close()
    }
    // Plato.
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 20.5f); horizontalLineTo(17.5f); verticalLineTo(22f); horizontalLineTo(3f); close()
    }
}.build()
