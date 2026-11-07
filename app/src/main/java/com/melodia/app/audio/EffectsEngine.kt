package com.melodia.app.audio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Guarda los efectos slow + reverb + tono. La reverberación va al procesador de
 * audio del servicio; velocidad y tono los aplica [com.melodia.app.player.PlayerController].
 */
class EffectsEngine(context: Context) {

    val reverb = ReverbAudioProcessor()
    val vocal = VocalRemoverAudioProcessor()

    private val prefs = context.getSharedPreferences("effects", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<FxSettings> = _settings.asStateFlow()

    init {
        applyReverb(_settings.value)
    }

    private fun update(transform: (FxSettings) -> FxSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        applyReverb(next)
        save(next)
    }

    private fun applyReverb(s: FxSettings) {
        reverb.setParams(s.effReverbMix, s.roomSize, s.damping)
        // La función "off vocal" se quitó de la app: el procesador queda siempre apagado.
        vocal.setParams(false, s.vocalAmount)
    }

    fun setVocalOff(on: Boolean) = update { it.copy(vocalOff = on) }
    fun setVocalAmount(v: Float) = update { it.copy(vocalAmount = v.coerceIn(0f, 1f)) }

    private fun roundSpeed(v: Float) = Math.round(v.coerceIn(SPEED_MIN, SPEED_MAX) * 100f) / 100f
    private fun roundPitch(v: Float) = Math.round(v.coerceIn(PITCH_MIN_ST, PITCH_MAX_ST) * 10f) / 10f

    /**
     * Con los controles unidos, el tono acompaña a la velocidad (y al revés). Se calcula siempre desde
     * el punto de partida del vínculo, así los redondeos no se acumulan al arrastrar.
     */
    fun setSpeed(v: Float) = update {
        val speed = roundSpeed(v)
        val pitch = if (it.linked) roundPitch(it.anchorPitch + (speed - it.anchorSpeed) * FX_LINK_ST_PER_X) else it.pitchSt
        it.copy(speed = speed, pitchSt = pitch, preset = CUSTOM, enabled = true)
    }

    fun setPitch(st: Float) = update {
        val pitch = roundPitch(st)
        val speed = if (it.linked) roundSpeed(it.anchorSpeed + (pitch - it.anchorPitch) / FX_LINK_ST_PER_X) else it.speed
        it.copy(speed = speed, pitchSt = pitch, preset = CUSTOM, enabled = true)
    }

    /** Une o separa velocidad y tono; al unirlos, los valores actuales son el nuevo punto de partida. */
    fun setLinked(on: Boolean) = update {
        it.copy(linked = on, anchorSpeed = it.speed, anchorPitch = it.pitchSt)
    }

    fun setReverbMix(v: Float) = update { it.copy(reverbMix = v.coerceIn(0f, 1f), preset = CUSTOM, enabled = true) }
    fun setRoomSize(v: Float) = update { it.copy(roomSize = v.coerceIn(0f, 1f), preset = CUSTOM, enabled = true) }
    fun setDamping(v: Float) = update { it.copy(damping = v.coerceIn(0f, 1f), preset = CUSTOM, enabled = true) }

    /** Enciende o apaga el efecto (conserva los valores). */
    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }
    fun toggle() = update { it.copy(enabled = !it.enabled) }

    /** Valores iniciales (0.85× y −3.5 st) y efecto apagado (no toca off vocal ni el vínculo). */
    fun reset() = update { FxSettings(vocalOff = it.vocalOff, vocalAmount = it.vocalAmount, linked = it.linked) }

    // --- Ajustes propios ------------------------------------------------------

    private val _profiles = MutableStateFlow(loadProfiles())
    /** Ajustes guardados por el usuario. */
    val profiles: StateFlow<List<FxProfile>> = _profiles.asStateFlow()

    /** Guarda los valores actuales con un nombre (reemplaza si ya existe uno igual). */
    fun saveProfile(name: String) {
        val clean = name.trim().replace("\n", " ").ifBlank { return }
        val s = _settings.value
        val profile = FxProfile(clean, s.speed, s.pitchSt, s.reverbMix, s.roomSize, s.damping)
        _profiles.value = _profiles.value.filterNot { it.name == clean } + profile
        storeProfiles()
        update { it.copy(preset = clean) }
    }

    /** Aplica un ajuste guardado y enciende el efecto. */
    fun applyProfile(p: FxProfile) = update {
        it.copy(
            speed = p.speed, pitchSt = p.pitchSt, reverbMix = p.reverbMix,
            roomSize = p.roomSize, damping = p.damping, preset = p.name, enabled = true,
            anchorSpeed = p.speed, anchorPitch = p.pitchSt
        )
    }

    fun deleteProfile(name: String) {
        _profiles.value = _profiles.value.filterNot { it.name == name }
        storeProfiles()
    }

    private fun storeProfiles() {
        val arr = org.json.JSONArray()
        for (p in _profiles.value) {
            arr.put(
                JSONObject().put("name", p.name).put("speed", p.speed.toDouble()).put("pitch", p.pitchSt.toDouble())
                    .put("mix", p.reverbMix.toDouble()).put("room", p.roomSize.toDouble()).put("damp", p.damping.toDouble())
            )
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    private fun loadProfiles(): List<FxProfile> = runCatching {
        val arr = org.json.JSONArray(prefs.getString(KEY_PROFILES, null) ?: return@runCatching emptyList())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            FxProfile(
                o.getString("name"),
                o.optDouble("speed", 1.0).toFloat().coerceIn(SPEED_MIN, SPEED_MAX),
                o.optDouble("pitch", 0.0).toFloat().coerceIn(PITCH_MIN_ST, PITCH_MAX_ST),
                o.optDouble("mix", 0.0).toFloat().coerceIn(0f, 1f),
                o.optDouble("room", 0.6).toFloat().coerceIn(0f, 1f),
                o.optDouble("damp", 0.5).toFloat().coerceIn(0f, 1f)
            )
        }
    }.getOrDefault(emptyList())

    private fun save(s: FxSettings) {
        val json = JSONObject()
            .put("speed", s.speed.toDouble()).put("pitch", s.pitchSt.toDouble())
            .put("mix", s.reverbMix.toDouble()).put("room", s.roomSize.toDouble())
            .put("damp", s.damping.toDouble()).put("preset", s.preset)
            .put("voff", s.vocalOff).put("vamt", s.vocalAmount.toDouble()).put("on", s.enabled)
            .put("link", s.linked).put("aS", s.anchorSpeed.toDouble()).put("aP", s.anchorPitch.toDouble())
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): FxSettings = runCatching {
        val j = JSONObject(prefs.getString(KEY, null) ?: return@runCatching FxSettings())
        val speed0 = j.optDouble("speed", 1.0).toFloat().coerceIn(SPEED_MIN, SPEED_MAX)
        val pitch0 = j.optDouble("pitch", 0.0).toFloat().coerceIn(PITCH_MIN_ST, PITCH_MAX_ST)
        val mix0 = j.optDouble("mix", 0.0).toFloat().coerceIn(0f, 1f)
        // Velocidad y tono sin tocar (1× y 0 st) con el efecto apagado: pasan a los nuevos valores
        // iniciales. Si el efecto está encendido (p. ej. solo reverb), se respeta tal cual suena.
        val wasOn = if (j.has("on")) j.optBoolean("on") else !(speed0 == 1f && pitch0 == 0f && mix0 == 0f)
        val untouched = speed0 == 1f && pitch0 == 0f && !wasOn
        val speed = if (untouched) FX_DEFAULT_SPEED else speed0
        val pitch = if (untouched) FX_DEFAULT_PITCH else pitch0
        FxSettings(
            // Ajustes de versiones anteriores (sin "on"): si ya tenían efecto, siguen encendidos.
            enabled = if (j.has("on")) j.optBoolean("on") else !(speed0 == 1f && pitch0 == 0f && mix0 == 0f),
            speed = speed,
            pitchSt = pitch,
            linked = j.optBoolean("link", true),
            anchorSpeed = j.optDouble("aS", speed.toDouble()).toFloat().coerceIn(SPEED_MIN, SPEED_MAX),
            anchorPitch = j.optDouble("aP", pitch.toDouble()).toFloat().coerceIn(PITCH_MIN_ST, PITCH_MAX_ST),
            reverbMix = j.optDouble("mix", 0.0).toFloat().coerceIn(0f, 1f),
            roomSize = j.optDouble("room", 0.6).toFloat().coerceIn(0f, 1f),
            damping = j.optDouble("damp", 0.5).toFloat().coerceIn(0f, 1f),
            preset = j.optString("preset", "Normal"),
            vocalOff = j.optBoolean("voff", false),
            vocalAmount = j.optDouble("vamt", 0.8).toFloat().coerceIn(0f, 1f)
        )
    }.getOrDefault(FxSettings())

    companion object {
        const val CUSTOM = "Personalizado"
        private const val KEY = "fx_v1"
        private const val KEY_PROFILES = "fx_profiles_v1"
    }
}
