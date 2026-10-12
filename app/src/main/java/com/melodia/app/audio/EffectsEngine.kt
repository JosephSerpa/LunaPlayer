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
        reverb.setParams(s.reverbMix, s.roomSize, s.damping)
        vocal.setParams(s.vocalOff, s.vocalAmount)
    }

    fun setVocalOff(on: Boolean) = update { it.copy(vocalOff = on) }
    fun setVocalAmount(v: Float) = update { it.copy(vocalAmount = v.coerceIn(0f, 1f)) }

    fun setSpeed(v: Float) = update {
        it.copy(speed = (Math.round(v.coerceIn(SPEED_MIN, SPEED_MAX) * 100f) / 100f), preset = CUSTOM)
    }

    fun setPitch(st: Float) = update {
        it.copy(pitchSt = (Math.round(st.coerceIn(PITCH_MIN_ST, PITCH_MAX_ST) * 10f) / 10f), preset = CUSTOM)
    }

    fun setReverbMix(v: Float) = update { it.copy(reverbMix = v.coerceIn(0f, 1f), preset = CUSTOM) }
    fun setRoomSize(v: Float) = update { it.copy(roomSize = v.coerceIn(0f, 1f), preset = CUSTOM) }
    fun setDamping(v: Float) = update { it.copy(damping = v.coerceIn(0f, 1f), preset = CUSTOM) }

    fun applyPreset(p: FxPreset) = update {
        p.settings.copy(preset = p.name, vocalOff = it.vocalOff, vocalAmount = it.vocalAmount)
    }

    fun reset() = update { FxSettings(vocalOff = it.vocalOff, vocalAmount = it.vocalAmount) }

    private fun save(s: FxSettings) {
        val json = JSONObject()
            .put("speed", s.speed.toDouble()).put("pitch", s.pitchSt.toDouble())
            .put("mix", s.reverbMix.toDouble()).put("room", s.roomSize.toDouble())
            .put("damp", s.damping.toDouble()).put("preset", s.preset)
            .put("voff", s.vocalOff).put("vamt", s.vocalAmount.toDouble())
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): FxSettings = runCatching {
        val j = JSONObject(prefs.getString(KEY, null) ?: return@runCatching FxSettings())
        FxSettings(
            speed = j.optDouble("speed", 1.0).toFloat().coerceIn(SPEED_MIN, SPEED_MAX),
            pitchSt = j.optDouble("pitch", 0.0).toFloat().coerceIn(PITCH_MIN_ST, PITCH_MAX_ST),
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
    }
}
