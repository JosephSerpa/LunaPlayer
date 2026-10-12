package com.melodia.app.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Guarda los ajustes del ecualizador, los envía al procesador de audio y
 * los recuerda entre sesiones.
 */
class EqualizerEngine(context: Context) {

    val processor = EqualizerAudioProcessor()

    private val prefs = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    private val _settings = MutableStateFlow(decode(prefs.getString(KEY, null)))
    val settings: StateFlow<EqSettings> = _settings.asStateFlow()

    init {
        processor.setSettings(_settings.value)
    }

    private fun update(transform: (EqSettings) -> EqSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        processor.setSettings(next)
        scheduleSave()
    }

    private fun EqSettings.withBand(index: Int, change: (EqBand) -> EqBand): EqSettings =
        copy(
            bands = bands.mapIndexed { i, b -> if (i == index) change(b) else b },
            preset = CUSTOM
        )

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setPreamp(db: Float) =
        update { it.copy(preampDb = db.coerceIn(PREAMP_MIN_DB, PREAMP_MAX_DB)) }

    fun setGain(index: Int, db: Float) =
        update { s -> s.withBand(index) { it.copy(gainDb = db.coerceIn(EQ_MIN_DB, EQ_MAX_DB)) } }

    fun setFrequency(index: Int, hz: Float) =
        update { s -> s.withBand(index) { it.copy(freqHz = hz.coerceIn(FREQ_MIN, FREQ_MAX)) } }

    fun setQ(index: Int, q: Float) =
        update { s -> s.withBand(index) { it.copy(q = q.coerceIn(Q_MIN, Q_MAX)) } }

    /** Cambia el comportamiento de la curva del punto (y le pone el Q natural de ese tipo). */
    fun setType(index: Int, type: FilterType) =
        update { s -> s.withBand(index) { it.copy(type = type, q = type.defaultQ) } }

    /** Mueve un punto desde la gráfica: frecuencia y ganancia a la vez (la ganancia solo si el tipo la usa). */
    fun setFrequencyAndGain(index: Int, hz: Float, db: Float) {
        update { s ->
            s.withBand(index) {
                it.copy(
                    freqHz = hz.coerceIn(FREQ_MIN, FREQ_MAX),
                    gainDb = if (it.type.usesGain) db.coerceIn(EQ_MIN_DB, EQ_MAX_DB) else it.gainDb
                )
            }
        }
    }

    /** Añade un punto (máximo 7) en el hueco más grande de la curva. Devuelve su índice o -1. */
    fun addBand(): Int {
        val current = _settings.value
        if (current.bands.size >= EQ_BANDS) return -1
        val sorted = (current.bands.map { it.freqHz } + listOf(GRAPH_MIN_HZ.toFloat(), GRAPH_MAX_HZ.toFloat()))
            .sorted()
        var bestMid = 1000f
        var bestGap = -1.0
        for (i in 0 until sorted.size - 1) {
            val gap = ln(sorted[i + 1].toDouble() / sorted[i].toDouble())
            if (gap > bestGap) {
                bestGap = gap
                bestMid = kotlin.math.sqrt(sorted[i].toDouble() * sorted[i + 1].toDouble()).toFloat()
            }
        }
        val hz = bestMid.coerceIn(FREQ_MIN, FREQ_MAX).let { if (it < 1000f) it.roundToInt().toFloat() else (it / 10f).roundToInt() * 10f }
        update { it.copy(bands = it.bands + EqBand(hz, 0f, 1.0f), preset = CUSTOM) }
        return _settings.value.bands.size - 1
    }

    /** Quita un punto (se puede dejar en 0). */
    fun removeBand(index: Int) {
        if (_settings.value.bands.size <= EQ_MIN_BANDS) return
        update { s ->
            s.copy(bands = s.bands.filterIndexed { i, _ -> i != index }, preset = CUSTOM)
        }
    }

    fun applyPreset(preset: EqPreset) =
        update { it.copy(bands = defaultBands(preset.gains), preset = preset.name) }

    // --- Perfiles propios ------------------------------------------------------

    private val _profiles = MutableStateFlow(loadProfiles())
    /** Perfiles guardados por el usuario (bandas, frecuencias, Q y ganancia previa). */
    val profiles: StateFlow<List<EqSettings>> = _profiles.asStateFlow()

    /** Guarda el ajuste actual como un perfil con nombre (reemplaza si ya existe). */
    fun saveProfile(name: String) {
        val clean = name.trim().replace(";", "").replace("|", "").ifBlank { return }
        val profile = _settings.value.copy(enabled = true, preset = clean)
        _profiles.value = _profiles.value.filterNot { it.preset == clean } + profile
        storeProfiles()
        update { it.copy(preset = clean) }
    }

    fun applyProfile(profile: EqSettings) = update {
        profile.copy(enabled = it.enabled)
    }

    fun deleteProfile(name: String) {
        _profiles.value = _profiles.value.filterNot { it.preset == name }
        storeProfiles()
    }

    private fun storeProfiles() {
        prefs.edit().putString(KEY_PROFILES, _profiles.value.joinToString("\n") { encode(it) }).apply()
    }

    private fun loadProfiles(): List<EqSettings> =
        prefs.getString(KEY_PROFILES, null).orEmpty()
            .split('\n').filter { it.isNotBlank() }
            .map { decode(it) }

    fun resetBand(index: Int) = update { s ->
        s.withBand(index) { band -> EqBand(DEFAULT_FREQS.getOrElse(index) { _ -> band.freqHz }, 0f, 1.0f) }
    }

    fun resetAll() = update {
        EqSettings(enabled = it.enabled, preampDb = 0f, bands = defaultBands(), preset = "Plano")
    }

    // --- Persistencia -------------------------------------------------------

    private fun scheduleSave() {
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 400)
    }

    private val saveRunnable = Runnable {
        prefs.edit().putString(KEY, encode(_settings.value)).apply()
    }

    private fun encode(s: EqSettings): String = buildString {
        append(if (s.enabled) "1" else "0").append(';')
        append(s.preampDb).append(';')
        append(s.preset.replace(";", "").replace("|", "")).append(';')
        append(s.bands.joinToString("|") { "${it.freqHz},${it.gainDb},${it.q},${it.type.name}" })
    }

    private fun decode(raw: String?): EqSettings {
        if (raw.isNullOrBlank()) return EqSettings()
        return runCatching {
            val parts = raw.split(';')
            val bandsText = parts.getOrNull(3).orEmpty()
            val bands = if (bandsText.isBlank()) emptyList() else bandsText.split('|').map { text ->
                val v = text.split(',')
                EqBand(
                    freqHz = v[0].toFloat().coerceIn(FREQ_MIN, FREQ_MAX),
                    gainDb = v[1].toFloat().coerceIn(EQ_MIN_DB, EQ_MAX_DB),
                    q = v[2].toFloat().coerceIn(Q_MIN, Q_MAX),
                    type = v.getOrNull(3)?.let { n -> runCatching { FilterType.valueOf(n) }.getOrNull() }
                        ?: FilterType.PEAK
                )
            }
            require(bands.size in EQ_MIN_BANDS..EQ_BANDS)
            EqSettings(
                enabled = parts[0] == "1",
                preampDb = parts[1].toFloat().coerceIn(PREAMP_MIN_DB, PREAMP_MAX_DB),
                bands = bands,
                preset = parts[2].ifBlank { CUSTOM }
            )
        }.getOrElse { EqSettings() }
    }

    private companion object {
        const val FREQ_MIN = 20f
        const val FREQ_MAX = 20000f
        const val KEY = "settings_v1"
        const val KEY_PROFILES = "profiles_v1"
        const val CUSTOM = "Personalizado"
    }
}
