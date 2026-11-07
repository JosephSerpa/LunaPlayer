package com.melodia.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.melodia.app.MainActivity
import com.melodia.app.MelodiaApp
import com.melodia.app.R
import com.melodia.app.data.LibraryCache
import com.melodia.app.data.ServerDb
import com.melodia.app.data.Song
import com.melodia.app.data.server.ServerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Widget de la pantalla principal del teléfono: la carátula de lo que suena (y su color, difuminado
 * de fondo), título, artista y controles. Tiene tres diseños según el tamaño: pequeño (carátula
 * grande), alargado de una fila y el completo con "me gusta".
 */
object PlayerWidget {

    data class State(
        val mediaId: String = "",
        val title: String = "",
        val artist: String = "",
        val artUri: String? = null,
        val playing: Boolean = false,
        val hasItem: Boolean = false,
        val shuffle: Boolean = false,
        val repeat: Int = Player.REPEAT_MODE_OFF
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var state: State? = null
    private var art: Bitmap? = null
    private var blur: Bitmap? = null
    private var tint: Int = DEFAULT_TINT
    /** Color vivo de la carátula: estrellas, corazón y botones activos (como en el reproductor). */
    private var accent: Int = DEFAULT_ACCENT
    private var artFor: String? = null
    private var artJob: Job? = null
    private var watching = false

    const val ACTION_PLAY = "com.lunaplayer.widget.PLAY"
    const val ACTION_NEXT = "com.lunaplayer.widget.NEXT"
    const val ACTION_PREV = "com.lunaplayer.widget.PREV"
    const val ACTION_LIKE = "com.lunaplayer.widget.LIKE"
    const val ACTION_SHUFFLE = "com.lunaplayer.widget.SHUFFLE"
    const val ACTION_REPEAT = "com.lunaplayer.widget.REPEAT"
    const val ACTION_RATE = "com.lunaplayer.widget.RATE"
    private const val DEFAULT_TINT = 0xFF2A2140.toInt()
    private const val DEFAULT_ACCENT = 0xFFFF8FB8.toInt()

    /** Lo llama el servicio de reproducción cada vez que cambia la canción o si suena. */
    fun push(context: Context, p: Player) {
        val item = p.currentMediaItem
        val m = item?.mediaMetadata
        val s = State(
            mediaId = item?.mediaId.orEmpty(),
            title = m?.title?.toString().orEmpty(),
            artist = m?.artist?.toString().orEmpty(),
            artUri = m?.artworkUri?.toString(),
            playing = p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE,
            hasItem = item != null,
            // El aleatorio es propio de la app (no el del reproductor): se lee de la sesión guardada.
            shuffle = context.getSharedPreferences("session", Context.MODE_PRIVATE).getBoolean("shuffle", false),
            repeat = p.repeatMode
        )
        set(context.applicationContext, s)
    }

    /** El servicio se cerró: queda la última canción, en pausa. */
    fun stopped(context: Context) {
        val s = state ?: load(context) ?: return
        set(context.applicationContext, s.copy(playing = false, hasItem = false))
    }

    private fun set(context: Context, s: State) {
        watchLikes(context)
        if (s == state) return
        state = s
        save(context, s)
        if (s.artUri != artFor) loadArt(context, s.artUri) else render(context)
    }

    /** Al cambiar "me gusta" (desde la app o el widget) se actualiza el corazón. */
    private fun watchLikes(context: Context) {
        if (watching) return
        watching = true
        val app = context.applicationContext as MelodiaApp
        scope.launch {
            app.favorites.marks
                .map { marks -> state?.mediaId?.toLongOrNull()?.let { marks[it] } }
                .distinctUntilChanged()
                .collect { render(context) }
        }
    }

    /** Vuelve a dibujar todos los widgets (al añadir uno, cambiar de tamaño, reiniciar el teléfono…). */
    fun refresh(context: Context) {
        val c = context.applicationContext
        val s = state ?: load(c) ?: State()
        state = s
        if (s.artUri != null && art == null) loadArt(c, s.artUri) else render(c)
    }

    private fun loadArt(context: Context, uri: String?) {
        artJob?.cancel()
        artFor = uri
        if (uri == null) {
            art = null; blur = null; tint = DEFAULT_TINT; accent = DEFAULT_ACCENT
            render(context); return
        }
        artJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val r = context.imageLoader.execute(
                        ImageRequest.Builder(context).data(Uri.parse(uri)).size(320).allowHardware(false).build()
                    )
                    val bmp = ((r as? SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        ?: return@runCatching null
                    val square = centerSquare(bmp, 320)
                    val p = Palette.from(square).generate()
                    val base = p.darkVibrantSwatch?.rgb ?: p.darkMutedSwatch?.rgb ?: p.dominantSwatch?.rgb ?: DEFAULT_TINT
                    // Oscuro para que el texto blanco siempre se lea.
                    var c = base
                    repeat(8) { if (ColorUtils.calculateLuminance(c) > 0.08) c = ColorUtils.blendARGB(c, 0xFF000000.toInt(), 0.22f) }
                    // Acento claro y vivo, que se lea sobre el fondo oscuro.
                    var acc = p.lightVibrantSwatch?.rgb ?: p.vibrantSwatch?.rgb ?: p.lightMutedSwatch?.rgb ?: DEFAULT_ACCENT
                    repeat(6) { if (ColorUtils.calculateLuminance(acc) < 0.35) acc = ColorUtils.blendARGB(acc, 0xFFFFFFFF.toInt(), 0.25f) }
                    listOf(rounded(square, 0.12f), rounded(softBlur(square), 0.08f), c, acc)
                }.getOrNull()
            }
            art = result?.get(0) as Bitmap?; blur = result?.get(1) as Bitmap?
            tint = result?.get(2) as Int? ?: DEFAULT_TINT; accent = result?.get(3) as Int? ?: DEFAULT_ACCENT
            render(context)
        }
    }

    private fun render(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val s = state ?: State()
        val app = context.applicationContext as MelodiaApp
        val mark = s.mediaId.toLongOrNull()?.let { app.favorites.marks.value[it] }
        val liked = mark?.liked == true
        val rating = mark?.rating ?: 0
        for (id in ids) {
            val views = if (Build.VERSION.SDK_INT >= 31) {
                // Android elige, entre los que caben, el diseño de tamaño más cercano al del widget.
                // Cada diseño está pensado para caber completo en su tamaño mínimo: nada se corta.
                RemoteViews(SIZES.associate { (w, h, l) -> SizeF(w, h) to build(context, l, s, liked, rating) })
            } else {
                build(context, layoutFor(mgr.getAppWidgetOptions(id)), s, liked, rating)
            }
            runCatching { mgr.updateAppWidget(id, views) }
        }
    }

    /** (ancho mínimo, alto mínimo, diseño), en dp. */
    private val SIZES = listOf(
        Triple(100f, 100f, R.layout.widget_player_small),
        Triple(180f, 110f, R.layout.widget_player_small),
        Triple(180f, 50f, R.layout.widget_player_mini_s),
        Triple(260f, 50f, R.layout.widget_player_mini),
        Triple(260f, 110f, R.layout.widget_player_wide_a),
        Triple(260f, 250f, R.layout.widget_player_wide_a),
        Triple(330f, 120f, R.layout.widget_player_wide_b),
        Triple(330f, 250f, R.layout.widget_player_wide_b),
        Triple(360f, 120f, R.layout.widget_player_wide_c),
        Triple(360f, 250f, R.layout.widget_player_wide_c),
        Triple(430f, 130f, R.layout.widget_player_wide_d),
        Triple(430f, 250f, R.layout.widget_player_wide_d),
        Triple(220f, 370f, R.layout.widget_player_large),
        Triple(360f, 370f, R.layout.widget_player_large_w)
    )

    /** Antes de Android 12 se elige el diseño a mano según el tamaño del widget. */
    private fun layoutFor(o: Bundle): Int {
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 330)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
        return when {
            h < 110 -> if (w >= 260) R.layout.widget_player_mini else R.layout.widget_player_mini_s
            w < 260 -> if (h >= 370 && w >= 220) R.layout.widget_player_large else R.layout.widget_player_small
            h >= 370 -> if (w >= 360) R.layout.widget_player_large_w else R.layout.widget_player_large
            w >= 430 && h >= 130 -> R.layout.widget_player_wide_d
            w >= 360 && h >= 120 -> R.layout.widget_player_wide_c
            w >= 330 && h >= 120 -> R.layout.widget_player_wide_b
            else -> R.layout.widget_player_wide_a
        }
    }

    private fun build(context: Context, layout: Int, s: State, liked: Boolean, rating: Int): RemoteViews {
        val v = RemoteViews(context.packageName, layout)
        val idle = s.title.isBlank()
        v.setTextViewText(R.id.title, if (idle) context.getString(R.string.widget_idle) else s.title)
        v.setTextViewText(R.id.artist, if (idle) context.getString(R.string.widget_idle_sub) else s.artist)
        val a = art
        if (a != null) {
            v.setImageViewBitmap(R.id.art, a)
            v.setViewPadding(R.id.art, 0, 0, 0, 0)
        } else {
            v.setImageViewResource(R.id.art, R.drawable.ic_w_note)
            val pad = (context.resources.displayMetrics.density * when (layout) {
                R.layout.widget_player_small, R.layout.widget_player_large, R.layout.widget_player_large_w -> 36
                R.layout.widget_player_mini, R.layout.widget_player_mini_s -> 6
                else -> 14
            }).toInt()
            v.setViewPadding(R.id.art, pad, pad, pad, pad)
        }
        val small = layout == R.layout.widget_player_small
        val large = layout == R.layout.widget_player_large || layout == R.layout.widget_player_large_w
        val hasPrevNext = !small && layout != R.layout.widget_player_mini_s
        val hasLike = layout != R.layout.widget_player_mini && layout != R.layout.widget_player_mini_s
        if (small) v.setInt(R.id.bg_tint2, "setColorFilter", tint)
        if (!small) {
            val b = blur
            if (b != null) v.setImageViewBitmap(R.id.bg_art, b) else v.setImageViewResource(R.id.bg_art, android.R.color.transparent)
        }
        v.setInt(R.id.bg_tint, "setColorFilter", tint)
        v.setImageViewResource(R.id.btn_play, if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)

        val open = PendingIntent.getActivity(
            context, 40,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_PLAYER
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        v.setOnClickPendingIntent(R.id.root, open)
        // Sin nada en la cola (y sin canción guardada) el botón abre la app.
        v.setOnClickPendingIntent(R.id.btn_play, if (idle) open else action(context, ACTION_PLAY, 41))
        if (hasPrevNext) {
            v.setOnClickPendingIntent(R.id.btn_prev, action(context, ACTION_PREV, 42))
            v.setOnClickPendingIntent(R.id.btn_next, action(context, ACTION_NEXT, 43))
        }
        if (hasLike) {
            v.setImageViewResource(R.id.btn_like, if (liked) R.drawable.ic_w_heart else R.drawable.ic_w_heart_border)
            v.setInt(R.id.btn_like, "setColorFilter", if (liked) accent else 0xFFFFFFFF.toInt())
            v.setOnClickPendingIntent(R.id.btn_like, action(context, ACTION_LIKE, 44))
        }
        if (large) {
            val stars = intArrayOf(R.id.star_1, R.id.star_2, R.id.star_3, R.id.star_4, R.id.star_5)
            for ((i, sid) in stars.withIndex()) {
                v.setInt(sid, "setColorFilter", if (rating > i) accent else 0x59FFFFFF)
                v.setOnClickPendingIntent(sid, action(context, "$ACTION_RATE${i + 1}", 51 + i))
            }
            v.setInt(R.id.btn_shuffle, "setColorFilter", if (s.shuffle) accent else 0x99FFFFFF.toInt())
            v.setImageViewResource(R.id.btn_repeat, if (s.repeat == Player.REPEAT_MODE_ONE) R.drawable.ic_w_repeat_one else R.drawable.ic_w_repeat)
            v.setInt(R.id.btn_repeat, "setColorFilter", if (s.repeat != Player.REPEAT_MODE_OFF) accent else 0x99FFFFFF.toInt())
            v.setOnClickPendingIntent(R.id.btn_shuffle, action(context, ACTION_SHUFFLE, 45))
            v.setOnClickPendingIntent(R.id.btn_repeat, action(context, ACTION_REPEAT, 46))
        }
        return v
    }

    private fun action(context: Context, what: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, PlayerWidgetProvider::class.java).setAction(what),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Botones del widget. Si la app estaba cerrada y no hay cola, retoma la última sesión. */
    fun handle(context: Context, what: String) {
        val app = context.applicationContext as MelodiaApp
        val c = app.playerController
        when (what) {
            ACTION_PLAY -> c.playPauseOrRestore { lastLibrary(app) }
            ACTION_NEXT -> c.next()
            ACTION_PREV -> c.previous()
            ACTION_LIKE -> (state ?: load(app))?.mediaId?.toLongOrNull()?.let { app.favorites.toggleLike(it) }
            ACTION_SHUFFLE -> {
                c.toggleShuffle()
                // El aleatorio no siempre cambia la cola: se vuelve a leer y se redibuja.
                scope.launch {
                    delay(400)
                    val on = app.getSharedPreferences("session", Context.MODE_PRIVATE).getBoolean("shuffle", false)
                    state = (state ?: State()).copy(shuffle = on)
                    render(app)
                }
            }
            ACTION_REPEAT -> c.cycleRepeat()
            else -> if (what.startsWith(ACTION_RATE)) {
                val n = what.removePrefix(ACTION_RATE).toIntOrNull() ?: return
                (state ?: load(app))?.mediaId?.toLongOrNull()?.let { app.favorites.setRating(it, n) }
            }
        }
    }

    /** La biblioteca guardada (teléfono + servidor), para retomar la cola sin abrir la app. */
    private fun lastLibrary(app: MelodiaApp): List<Song> = runCatching {
        val local = LibraryCache(app).read("local")?.songs.orEmpty()
        val cfg = app.serverSettings.config.value
        val remote = if (cfg.isActive) {
            val tag = ServerSettings.md5("${cfg.url}|${cfg.user}|${cfg.token}|${cfg.maxBitRate}")
            ServerDb.get(app).read(tag).orEmpty()
        } else emptyList()
        local + remote
    }.getOrDefault(emptyList())

    // --- Guardado del último estado (para dibujar el widget tras reiniciar) ---

    private fun save(context: Context, s: State) {
        context.getSharedPreferences("widget", Context.MODE_PRIVATE).edit()
            .putString("id", s.mediaId).putString("title", s.title).putString("artist", s.artist)
            .putString("art", s.artUri).apply()
    }

    private fun load(context: Context): State? {
        val p = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
        val title = p.getString("title", null) ?: return null
        return State(p.getString("id", "").orEmpty(), title, p.getString("artist", "").orEmpty(), p.getString("art", null), false, title.isNotBlank())
    }

    // --- Imágenes ---

    private fun centerSquare(src: Bitmap, size: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val crop = Bitmap.createBitmap(src, x, y, side, side)
        return Bitmap.createScaledBitmap(crop, size, size, true)
    }

    /** Desenfoque suave: se reduce mucho y se vuelve a agrandar (en dos pasos, sin bloques). */
    private fun softBlur(src: Bitmap): Bitmap {
        val tiny = Bitmap.createScaledBitmap(src, 12, 12, true)
        val mid = Bitmap.createScaledBitmap(tiny, 48, 48, true)
        return Bitmap.createScaledBitmap(mid, 160, 160, true)
    }

    private fun rounded(src: Bitmap, radiusFraction: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        val r = src.width * radiusFraction
        Canvas(out).drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), r, r, paint)
        return out
    }
}

class PlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        PlayerWidget.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        PlayerWidget.refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (val a = intent.action) {
            PlayerWidget.ACTION_PLAY, PlayerWidget.ACTION_NEXT, PlayerWidget.ACTION_PREV, PlayerWidget.ACTION_LIKE,
            PlayerWidget.ACTION_SHUFFLE, PlayerWidget.ACTION_REPEAT -> PlayerWidget.handle(context, a)
            else -> if (a?.startsWith(PlayerWidget.ACTION_RATE) == true) PlayerWidget.handle(context, a)
            else super.onReceive(context, intent)
        }
    }
}
