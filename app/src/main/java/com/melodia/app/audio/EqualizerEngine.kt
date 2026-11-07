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
class EqualizerEngine(private val context: Context) {

    val processor = EqualizerAudioProcessor()

    private val prefs = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    private val _settings = MutableStateFlow(decode(prefs.getString(KEY, null)))
    val settings: StateFlow<EqSettings> = _settings.asStateFlow()

    private val _autoSave = MutableStateFlow(prefs.getBoolean(KEY_AUTOSAVE, true))
    /** Si está activo, los cambios se guardan solos en el perfil propio que esté en uso. */
    val autoSave: StateFlow<Boolean> = _autoSave.asStateFlow()

    fun setAutoSave(on: Boolean) {
        _autoSave.value = on
        prefs.edit().putBoolean(KEY_AUTOSAVE, on).apply()
        if (on) scheduleSave()
    }

    /** Con autoguardado y un perfil propio en uso, el nombre se mantiene; si no, pasa a "Personalizado". */
    private fun nameAfterEdit(s: EqSettings): String =
        if (_autoSave.value && _profiles.value.any { it.preset == s.preset }) s.preset else CUSTOM

    // --- Ecualizador global (todo el dispositivo) ---------------------------

    private val globalEq = GlobalEq()
    private val _global = MutableStateFlow(false)
    /** Verdadero si el ecualizador está actuando sobre todo el dispositivo. */
    val global: StateFlow<Boolean> = _global.asStateFlow()

    /** Activa o desactiva el modo global. Devuelve falso si este dispositivo no lo permite. */
    fun setGlobal(on: Boolean): Boolean {
        if (on) {
            if (!globalEq.start(_settings.value)) return false
            runCatching {
                context.startForegroundService(android.content.Intent(context, GlobalEqService::class.java))
            }
        } else {
            globalEq.stop()
            runCatching { context.stopService(android.content.Intent(context, GlobalEqService::class.java)) }
        }
        _global.value = on
        prefs.edit().putBoolean(KEY_GLOBAL, on).apply()
        processor.setSettings(forProcessor(_settings.value))
        return true
    }

    // El procesador propio se aparta si el global ya ecualiza todo (si no, la app sonaría ecualizada dos veces).
    private fun forProcessor(s: EqSettings) = if (_global.value) s.copy(enabled = false) else s

    init {
        processor.setSettings(_settings.value)
        // Si estaba activado la última vez, se vuelve a enganchar al arrancar.
        if (prefs.getBoolean(KEY_GLOBAL, false)) setGlobal(true)
    }

    private fun update(transform: (EqSettings) -> EqSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        processor.setSettings(forProcessor(next))
        if (_global.value) globalEq.apply(next)
        scheduleSave()
    }

    private fun EqSettings.withBand(index: Int, change: (EqBand) -> EqBand): EqSettings =
        copy(
            bands = bands.mapIndexed { i, b -> if (i == index) change(b) else b },
            preset = nameAfterEdit(this)
        )

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setBoost(db: Float) =
        update { it.copy(boostDb = db.coerceIn(0f, BOOST_MAX_DB)) }

    fun setLimiter(on: Boolean) = update { it.copy(limiter = on) }

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
        update { it.copy(bands = it.bands + EqBand(hz, 0f, 1.0f), preset = nameAfterEdit(it)) }
        return _settings.value.bands.size - 1
    }

    /** Quita un punto (se puede dejar en 0). */
    fun removeBand(index: Int) {
        if (_settings.value.bands.size <= EQ_MIN_BANDS) return
        update { s ->
            s.copy(bands = s.bands.filterIndexed { i, _ -> i != index }, preset = nameAfterEdit(s))
        }
    }

    fun applyPreset(preset: EqPreset) =
        update { it.copy(bands = defaultBands(preset.gains), preset = preset.name) }

    // --- Perfiles propios ------------------------------------------------------

    private val _profiles = MutableStateFlow(loadProfiles())
    /** Perfiles guardados por el usuario (bandas, frecuencias, Q y ganancia previa). */
    val profiles: StateFlow<List<EqSettings>> = _profiles.asStateFlow()

    /**
     * Guarda el ajuste actual como un perfil con nombre (reemplaza si ya existe).
     * Si se indica un [device], el perfil se pondrá solo cada vez que ese dispositivo se conecte.
     */
    fun saveProfile(name: String, device: OutputDevice? = null) {
        val clean = name.trim().replace(";", "").replace("|", "").replace("=", "").ifBlank { return }
        val profile = _settings.value.copy(enabled = true, preset = clean)
        _profiles.value = _profiles.value.filterNot { it.preset == clean } + profile
        storeProfiles()
        if (device != null) assign(device, clean)
        update { it.copy(preset = clean) }
    }

    fun applyProfile(profile: EqSettings) = update {
        profile.copy(enabled = it.enabled)
    }

    fun deleteProfile(name: String) {
        _profiles.value = _profiles.value.filterNot { it.preset == name }
        storeProfiles()
        _assignments.value = _assignments.value.filterValues { it.profile != name }
        storeAssignments()
    }

    // --- Perfiles por dispositivo ---------------------------------------------

    private val audioManager = context.getSystemService(android.media.AudioManager::class.java)

    private val _devices = MutableStateFlow(currentDevices())
    /** Dispositivos de salida conectados ahora (altavoz, auriculares, Bluetooth, USB…). */
    val devices: StateFlow<List<OutputDevice>> = _devices.asStateFlow()

    private val _assignments = MutableStateFlow(loadAssignments())
    /** Qué perfil se pone al conectar cada dispositivo (clave del dispositivo → asignación). */
    val assignments: StateFlow<Map<String, Assignment>> = _assignments.asStateFlow()

    data class Assignment(val profile: String, val deviceName: String)

    private fun assign(device: OutputDevice, profile: String) {
        // Un dispositivo solo tiene un perfil: el nuevo reemplaza al anterior.
        _assignments.value = _assignments.value + (device.key to Assignment(profile, device.name))
        storeAssignments()
    }

    fun unassign(profile: String) {
        _assignments.value = _assignments.value.filterValues { it.profile != profile }
        storeAssignments()
    }

    /** Dispositivo asignado a un perfil, si lo hay. */
    fun deviceOf(profile: String): String? =
        _assignments.value.values.firstOrNull { it.profile == profile }?.deviceName

    private fun currentDevices(): List<OutputDevice> =
        audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            .mapNotNull { OutputDevice.from(it) }
            .distinctBy { it.key }

    private fun registerDeviceWatcher() {
        audioManager.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) {
                val before = _devices.value.map { it.key }.toSet()
                _devices.value = currentDevices()
                for (d in added) {
                    val dev = OutputDevice.from(d) ?: continue
                    if (dev.key in before) continue
                    val a = _assignments.value[dev.key] ?: continue
                    _profiles.value.firstOrNull { it.preset == a.profile }?.let { applyProfile(it) }
                    break
                }
            }

            override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>) {
                _devices.value = currentDevices()
            }
        }, handler)
    }

    private fun storeAssignments() {
        prefs.edit().putString(
            KEY_ASSIGN,
            _assignments.value.entries.joinToString("\n") { (k, v) ->
                listOf(k, v.profile, v.deviceName).joinToString("=")
            }
        ).apply()
    }

    private fun loadAssignments(): Map<String, Assignment> =
        prefs.getString(KEY_ASSIGN, null).orEmpty().split('\n').mapNotNull { line ->
            val p = line.split('=')
            if (p.size == 3) p[0] to Assignment(p[1], p[2]) else null
        }.toMap()

    init {
        registerDeviceWatcher()
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
        EqSettings(enabled = it.enabled, preampDb = 0f, bands = defaultBands(), preset = "Plano", boostDb = 0f, limiter = it.limiter)
    }

    // --- Persistencia -------------------------------------------------------

    private fun scheduleSave() {
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 400)
    }

    private val saveRunnable = Runnable {
        val s = _settings.value
        prefs.edit().putString(KEY, encode(s)).apply()
        // Autoguardado: si hay un perfil propio en uso, se actualiza con lo que se ve ahora.
        if (_autoSave.value && _profiles.value.any { it.preset == s.preset }) {
            val updated = s.copy(enabled = true)
            _profiles.value = _profiles.value.map { if (it.preset == s.preset) updated else it }
            storeProfiles()
        }
    }

    private fun encode(s: EqSettings): String = buildString {
        append(if (s.enabled) "1" else "0").append(';')
        append(s.preampDb).append(';')
        append(s.preset.replace(";", "").replace("|", "")).append(';')
        append(s.bands.joinToString("|") { "${it.freqHz},${it.gainDb},${it.q},${it.type.name}" }).append(';')
        append(s.boostDb).append(';')
        append(if (s.limiter) "1" else "0")
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
                preset = parts[2].ifBlank { CUSTOM },
                boostDb = parts.getOrNull(4)?.toFloatOrNull()?.coerceIn(0f, BOOST_MAX_DB) ?: 0f,
                limiter = parts.getOrNull(5) != "0"
            )
        }.getOrElse { EqSettings() }
    }

    private companion object {
        const val FREQ_MIN = 20f
        const val FREQ_MAX = 20000f
        const val KEY = "settings_v1"
        const val KEY_PROFILES = "profiles_v1"
        const val KEY_GLOBAL = "global_v1"
        const val KEY_ASSIGN = "assign_v1"
        const val KEY_AUTOSAVE = "autosave_v1"
        const val CUSTOM = "Personalizado"
    }
}

/** Un dispositivo de salida de audio. [key] lo identifica entre conexiones (tipo + dirección o nombre). */
data class OutputDevice(val key: String, val name: String, val type: Int) {
    companion object {
        fun from(d: android.media.AudioDeviceInfo): OutputDevice? {
            val t = d.type
            val ok = t in setOf(
                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
                android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY,
                android.media.AudioDeviceInfo.TYPE_HDMI,
                android.media.AudioDeviceInfo.TYPE_LINE_ANALOG,
                android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
                android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
                android.media.AudioDeviceInfo.TYPE_HEARING_AID
            )
            if (!ok) return null
            val label = d.productName?.toString()?.trim().orEmpty()
            val id = d.address.ifBlank { label }
            val name = when {
                t == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> com.melodia.app.Str.get(com.melodia.app.R.string.eq_dev_speaker)
                label.isNotBlank() -> label
                else -> com.melodia.app.Str.get(com.melodia.app.R.string.eq_dev_wired)
            }
            return OutputDevice("$t|$id".replace("=", "").replace("\n", ""), name.replace("=", "").replace("\n", ""), t)
        }
    }
}
