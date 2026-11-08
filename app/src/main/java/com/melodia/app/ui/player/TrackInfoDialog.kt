package com.melodia.app.ui.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.melodia.app.MelodiaApp
import com.melodia.app.R
import com.melodia.app.Str
import com.melodia.app.data.server.SubsonicClient
import com.melodia.app.player.NowPlaying
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Lo que se sabe del archivo en sí (no de la canción): lo que importa a quien cuida el sonido. */
private class FileTech(
    val format: String = "",
    val mime: String = "",
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 0,
    val bitrateKbps: Int = 0,
    val sizeBytes: Long = 0L,
    val location: String = "",
    val trackGain: Double? = null,
    val trackPeak: Double? = null,
    val albumGain: Double? = null,
    val albumPeak: Double? = null
)

private class OutputInfo(val kind: Int, val name: String, val maxRate: Int)

private val LOSSLESS = setOf("FLAC", "ALAC", "WAV", "AIFF", "APE", "WV", "DSF", "DFF", "DSD", "TTA", "PCM")

/**
 * Ficha técnica de lo que suena: el archivo original (formato, frecuencia, bits, canales, tasa de bits,
 * tamaño, ReplayGain), cómo llega al reproductor (original o convertido por el servidor), qué
 * procesamiento se le aplica y por dónde sale el audio.
 */
@Composable
fun TrackInfoDialog(current: NowPlaying, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MelodiaApp
    val palette = LocalPalette.current
    val accent = accentColor
    val eq by app.equalizer.settings.collectAsState()
    val globalEq by app.equalizer.global.collectAsState()
    val fx by app.effects.settings.collectAsState()
    val uri = current.uri
    val remote = uri?.scheme == "http" || uri?.scheme == "https"

    val tech by produceState<FileTech?>(null, current.mediaId) {
        value = withContext(Dispatchers.IO) {
            runCatching { if (remote) remoteTech(app, uri!!) else localTech(context, uri, current) }.getOrNull()
                ?: FileTech(format = current.audio.format, sampleRate = current.audio.sampleRateHz, bitrateKbps = current.audio.bitrateKbps)
        }
    }
    val output = produceState<OutputInfo?>(null) { value = withContext(Dispatchers.IO) { runCatching { outputDevice(context) }.getOrNull() } }.value

    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .shadow(24.dp, shape)
                .background(palette.bg.copy(alpha = 0.97f), shape)
                .glass(shape, fill = 0.05f, border = 0.14f)
                .verticalScroll(rememberScrollState())
                .padding(22.dp)
        ) {
            Text(Str.get(R.string.ti_title), style = MaterialTheme.typography.titleLarge, color = palette.ink)
            Text(
                current.title, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
            )

            // --- Archivo ---
            Section(Str.get(R.string.ti_file), accent)
            val t = tech
            if (t == null) {
                Text(Str.get(R.string.ti_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val fmt = t.format.ifBlank { current.audio.format }
                Item(Str.get(R.string.ti_format), listOf(fmt, t.mime).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { DASH })
                if (fmt.isNotBlank()) {
                    Item(Str.get(R.string.ti_compression), Str.get(if (fmt.uppercase() in LOSSLESS) R.string.ti_lossless else R.string.ti_lossy))
                }
                Item(Str.get(R.string.ti_sample_rate), khz(t.sampleRate))
                Item(Str.get(R.string.ti_bit_depth), if (t.bitDepth > 0) Str.get(R.string.ti_bits_value, t.bitDepth) else DASH)
                Item(Str.get(R.string.ti_channels), channels(t.channels))
                Item(Str.get(R.string.ti_bitrate), if (t.bitrateKbps > 0) "${t.bitrateKbps} kbps" else DASH)
                Item(Str.get(R.string.ti_size), if (t.sizeBytes > 0) mb(t.sizeBytes) else DASH)
                Item(Str.get(R.string.ti_duration), time(current.durationMs))
                val hires = t.sampleRate > 48_000 || t.bitDepth > 16
                Item(Str.get(R.string.ti_hires), Str.get(if (hires) R.string.ti_yes else R.string.ti_no))
                if (t.trackGain != null) Item(Str.get(R.string.ti_rg_track), gain(t.trackGain, t.trackPeak))
                if (t.albumGain != null) Item(Str.get(R.string.ti_rg_album), gain(t.albumGain, t.albumPeak))
                if (t.location.isNotBlank()) Item(Str.get(R.string.ti_location), t.location, wrap = true)
            }

            // --- Reproducción ---
            Section(Str.get(R.string.ti_playback), accent)
            Item(Str.get(R.string.ti_source), Str.get(if (remote) R.string.ti_src_server else R.string.ti_src_phone))
            if (remote) {
                val toFormat = uri?.getQueryParameter("format")
                val toKbps = uri?.getQueryParameter("maxBitRate")?.toIntOrNull() ?: 0
                Item(
                    Str.get(R.string.ti_stream),
                    if (!toFormat.isNullOrBlank() && toKbps > 0) Str.get(R.string.ti_transcoded, toFormat.uppercase(Locale.ROOT), toKbps)
                    else Str.get(R.string.ti_original),
                    wrap = true
                )
            }
            val a = current.audio
            Item(
                Str.get(R.string.ti_decoded),
                listOf(
                    a.format,
                    khz(a.sampleRateHz).takeIf { a.sampleRateHz > 0 },
                    a.pcmBits.takeIf { it > 0 }?.let { Str.get(R.string.ti_bits_value, it) },
                    channels(a.channels).takeIf { a.channels > 0 },
                    a.bitrateKbps.takeIf { it > 0 }?.let { "$it kbps" }
                ).filter { !it.isNullOrBlank() }.joinToString(" · ").ifBlank { DASH },
                wrap = true
            )
            // Todo lo que modifica la señal antes de salir.
            val steps = buildList {
                if (eq.enabled && !globalEq) add(Str.get(R.string.ti_p_eq))
                if (eq.enabled && globalEq) add(Str.get(R.string.ti_p_global))
                if (eq.enabled && eq.boostDb > 0.05f) add(Str.get(R.string.ti_p_boost, "%.1f".format(eq.boostDb)))
                if (fx.enabled) add("Slow + Reverb (" + String.format(Locale.US, "%.2f×", fx.speed) + ")")
            }
            Item(Str.get(R.string.ti_processing), if (steps.isEmpty()) Str.get(R.string.ti_none) else steps.joinToString(" · "), wrap = true)

            // --- Salida ---
            Section(Str.get(R.string.ti_output), accent)
            if (output != null) {
                val kind = when (output.kind) {
                    1 -> Str.get(R.string.ti_out_usb)
                    2 -> Str.get(R.string.ti_out_wired)
                    3 -> Str.get(R.string.ti_out_bt)
                    else -> Str.get(R.string.ti_out_speaker)
                }
                Item(Str.get(R.string.ti_device), listOf(kind, output.name).filter { it.isNotBlank() }.distinct().joinToString(" · "), wrap = true)
                if (output.maxRate > 0) Item(Str.get(R.string.ti_max_rate), khz(output.maxRate))
            }
            Text(
                Str.get(R.string.ti_mixer_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Text(
                Str.get(R.string.help_ok),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

private const val DASH = "—"

@Composable
private fun Section(title: String, accent: androidx.compose.ui.graphics.Color) {
    Spacer(Modifier.height(16.dp))
    Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = accent)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Item(label: String, value: String, wrap: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            value, style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.ink,
            textAlign = TextAlign.End, maxLines = if (wrap) 4 else 1
        )
    }
}

private fun khz(hz: Int): String {
    if (hz <= 0) return DASH
    val k = hz / 1000.0
    return (if (hz % 1000 == 0) "%.0f".format(k) else "%.1f".format(k)) + " kHz"
}

private fun channels(n: Int): String = when {
    n <= 0 -> DASH
    n == 1 -> Str.get(R.string.ti_mono)
    n == 2 -> Str.get(R.string.ti_stereo)
    else -> Str.get(R.string.ti_multich, n)
}

private fun mb(bytes: Long): String = "%.1f MB".format(bytes / (1024.0 * 1024.0))

private fun time(ms: Long): String {
    if (ms <= 0) return DASH
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun gain(g: Double, peak: Double?): String =
    String.format(Locale.US, "%+.2f dB", g) + (peak?.takeIf { it > 0 }?.let { String.format(Locale.US, " · %.3f", it) } ?: "")

/** Archivo del teléfono: se lee directamente. */
private fun localTech(context: Context, uri: Uri?, np: NowPlaying): FileTech? {
    if (uri == null) return null
    var mime = ""
    var bitrate = 0
    var rate = 0
    var bits = 0
    var ch = 0
    val mmr = MediaMetadataRetriever()
    try {
        mmr.setDataSource(context, uri)
        mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
        bitrate = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0) / 1000
        if (Build.VERSION.SDK_INT >= 31) {
            rate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            bits = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
        }
    } catch (_: Exception) {
    } finally {
        runCatching { mmr.release() }
    }
    val ex = MediaExtractor()
    try {
        ex.setDataSource(context, uri, null)
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") != true) continue
            if (rate == 0 && f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (bitrate == 0 && f.containsKey(MediaFormat.KEY_BIT_RATE)) bitrate = f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
            if (bits == 0 && f.containsKey("bits-per-sample")) bits = f.getInteger("bits-per-sample")
            if (bits == 0 && f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                bits = when (f.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                    android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
                    android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                    android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                    android.media.AudioFormat.ENCODING_PCM_32BIT, android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
                    else -> 0
                }
            }
            break
        }
    } catch (_: Exception) {
    } finally {
        runCatching { ex.release() }
    }
    val a = np.audio
    // MP3/AAC no tienen profundidad de bits real (son con pérdida): no se inventa.
    if (a.format.uppercase() !in LOSSLESS) bits = 0
    return FileTech(
        format = a.format,
        mime = mime,
        sampleRate = rate.takeIf { it > 0 } ?: a.sampleRateHz,
        bitDepth = bits,
        channels = ch.takeIf { it > 0 } ?: a.channels,
        bitrateKbps = bitrate.takeIf { it > 0 } ?: a.bitrateKbps,
        sizeBytes = a.sizeBytes,
        location = a.path.replace(Regex("^/storage/emulated/\\d+/?"), "")
    )
}

/** Canción del servidor: el servidor describe el archivo original (antes de convertirlo). */
private suspend fun remoteTech(app: MelodiaApp, uri: Uri): FileTech? {
    val id = uri.getQueryParameter("id") ?: return null
    val c = app.serverSettings.config.value
    val s = SubsonicClient.songDetails(c, id) ?: return null
    val rg = s.optJSONObject("replayGain")
    fun d(o: org.json.JSONObject?, k: String): Double? = o?.takeIf { it.has(k) }?.optDouble(k)?.takeIf { !it.isNaN() }
    val trackGain = d(rg, "trackGain")
    val albumGain = d(rg, "albumGain")
    return FileTech(
        format = s.optString("suffix").uppercase(Locale.ROOT),
        mime = s.optString("contentType"),
        sampleRate = s.optInt("samplingRate", 0),
        bitDepth = s.optInt("bitDepth", 0),
        channels = s.optInt("channelCount", 0),
        bitrateKbps = s.optInt("bitRate", 0),
        sizeBytes = s.optLong("size", 0L),
        location = s.optString("path"),
        trackGain = trackGain?.takeIf { it != 0.0 || d(rg, "trackPeak") != null },
        trackPeak = d(rg, "trackPeak"),
        albumGain = albumGain?.takeIf { it != 0.0 || d(rg, "albumPeak") != null },
        albumPeak = d(rg, "albumPeak")
    )
}

/**
 * Por dónde sale el audio. Android no le dice a las apps qué dispositivo usa en cada momento, pero
 * sigue un orden fijo: USB, luego auriculares con cable, luego Bluetooth y si no, el altavoz.
 */
private fun outputDevice(context: Context): OutputInfo? {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    fun kindOf(d: AudioDeviceInfo): Int = when (d.type) {
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> 1
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG -> 2
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 3
        else -> if (Build.VERSION.SDK_INT >= 31 && (d.type == AudioDeviceInfo.TYPE_BLE_HEADSET || d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) 3
            else if (d.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) 4 else 0
    }
    val best = devices.filter { kindOf(it) > 0 }.minByOrNull { kindOf(it) } ?: return null
    val kind = kindOf(best)
    val name = if (kind == 4) "" else best.productName?.toString().orEmpty()
    return OutputInfo(kind, name, best.sampleRates.maxOrNull() ?: 0)
}
