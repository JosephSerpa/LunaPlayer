package com.melodia.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 * Disciplina de batería y calor: con la pantalla apagada o la app en segundo plano ninguna animación,
 * visualizador ni gesto decorativo debe seguir corriendo (en un DAP se escucha así casi todo el tiempo).
 */
object AppVisibility {
    /** La actividad principal la actualiza en onStart/onStop. */
    @Volatile var foreground = false
}

/** Verdadero solo mientras la pantalla está visible (estado STARTED o superior). */
@Composable
fun rememberIsForeground(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.STARTED)
}
