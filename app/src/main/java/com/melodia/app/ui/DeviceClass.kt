package com.melodia.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.max
import kotlin.math.min

/** Tipo de pantalla: reproductor de bolsillo (DAP), celular o tablet. Cada uno ajusta su distribución. */
enum class DeviceClass { DAP, PHONE, TABLET }

fun deviceClassOf(c: Configuration): DeviceClass {
    val small = min(c.screenWidthDp, c.screenHeightDp)
    val large = max(c.screenWidthDp, c.screenHeightDp)
    return when {
        small >= 600 -> DeviceClass.TABLET
        small < 360 || large < 700 -> DeviceClass.DAP
        else -> DeviceClass.PHONE
    }
}

val LocalDeviceClass = staticCompositionLocalOf { DeviceClass.PHONE }
