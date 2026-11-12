package com.melodia.app.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Modo DAP / bit-perfect. Con él encendido la app no toca el audio: ecualizador propio,
 * reverberación y velocidad/tono quedan fuera de la ruta y el sonido sale tal cual lo decodifica
 * el reproductor hacia la salida del sistema (DAC interno, balanceada o USB).
 */
class DapMode(context: Context) {
    private val prefs = context.getSharedPreferences("dap", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    init { active = _enabled.value }

    fun set(on: Boolean) {
        if (_enabled.value == on) return
        _enabled.value = on
        active = on
        prefs.edit().putBoolean(KEY, on).apply()
    }

    companion object {
        /** Lo leen los procesadores de audio (hilo de reproducción): por eso es @Volatile y estático. */
        @Volatile @JvmStatic var active = false
        private const val KEY = "dap_mode"
    }
}

/**
 * Base de los procesadores propios: si fallan (ROM modificada, formato raro) la reproducción sigue
 * sin ese efecto en vez de cerrarse, y con el modo DAP encendido dejan la cadena de audio.
 */
@OptIn(UnstableApi::class)
abstract class SafeAudioProcessor : BaseAudioProcessor() {

    @Volatile private var failed = false

    /** Trabajo real del efecto; puede lanzar excepciones. */
    protected abstract fun process(inputBuffer: ByteBuffer)

    /** Se llama al configurar: reinicia el estado de error. */
    protected fun resetFailure() { failed = false }

    final override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (failed || DapMode.active) { passthrough(inputBuffer, size); return }
        val start = inputBuffer.position()
        try {
            process(inputBuffer)
        } catch (_: Throwable) {
            failed = true
            inputBuffer.position(start)
            passthrough(inputBuffer, size)
        }
    }

    private fun passthrough(inputBuffer: ByteBuffer, size: Int) {
        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }
}
