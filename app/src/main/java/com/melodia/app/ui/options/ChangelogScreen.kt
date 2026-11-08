package com.melodia.app.ui.options

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.melodia.app.R
import com.melodia.app.Str
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.theme.ink

/**
 * Lo nuevo de cada versión, de forma simplificada: solo lo que cambió o se agregó. Lo que se agregó y
 * luego se quitó no aparece. Las versiones de solo correcciones van juntas con la anterior.
 */
private class Release(val version: String, val es: List<String>, val en: List<String>)

private val RELEASES = listOf(
    Release(
        "2.7.0",
        listOf(
            "Información técnica: toca la etiqueta de formato en el reproductor para ver el archivo a fondo (frecuencia, bits, canales, compresión, ReplayGain, conversión, procesamiento y salida).",
            "Ecualizador: botón de encendido junto al perfil, con animación al encender; autoguardado sobre la gráfica y el ecualizador global al final.",
            "Estrellas con partículas: cuanto más alta la puntuación, más estrellas saltan.",
            "Aviso del agujero negro más suave."
        ),
        listOf(
            "Technical info: tap the format label in the player to see the file in depth (sample rate, bits, channels, compression, ReplayGain, conversion, processing and output).",
            "Equalizer: power button next to the profile, with a turn-on animation; auto-save above the graph and the global equalizer at the end.",
            "Star particles: the higher the rating, the more stars burst out.",
            "Softer black hole hint."
        )
    ),
    Release(
        "2.6.9",
        listOf(
            "Ecualizador reordenado: perfil arriba, luego el ecualizador global y después los puntos con el interruptor.",
            "El interruptor del ecualizador dice «Activado» o «Desactivado».",
            "Vista general a todo el ancho y con bordes continuos; nada tapa las etiquetas de la gráfica."
        ),
        listOf(
            "Reordered equalizer: profile on top, then the global equalizer, then the points with the switch.",
            "The equalizer switch now says “On” or “Off”.",
            "Full-width overview with continuous edges; nothing covers the graph labels."
        )
    ),
    Release(
        "2.6.8",
        listOf(
            "Buscador con filtros: busca solo en canciones, artistas, álbumes, géneros o carpetas.",
            "Ordena los resultados por estrellas o con tus me gusta primero."
        ),
        listOf(
            "Search filters: look only in songs, artists, albums, genres or folders.",
            "Sort the results by stars or with your likes first."
        )
    ),
    Release(
        "2.6.7",
        listOf(
            "Novedades: este apartado, con los cambios de cada versión.",
            "Slow + Reverb empieza en 0.85× y −3.5 st.",
            "Nuevo interruptor para mover juntos velocidad y tono (activado).",
            "Agujero negro: al mantenerlo presionado aparece un agujero que crece sobre el dedo; al soltar se abre su menú.",
            "Línea de reproducción más limpia y con una carga más suave."
        ),
        listOf(
            "What's new: this section, with the changes of each version.",
            "Slow + Reverb starts at 0.85× and −3.5 st.",
            "New switch to move speed and pitch together (on).",
            "Black hole: hold it and a growing black hole appears above your finger; release to open its menu.",
            "Cleaner playback line with a smoother loading animation."
        )
    ),
    Release(
        "2.6.6",
        listOf(
            "Partículas al dar me gusta y al poner estrellas; corazón roto al quitar el me gusta.",
            "Botón «Cerrar» en la notificación del ecualizador global.",
            "El botón de reproducir responde aunque la canción esté cargando, y se reintenta solo si se corta la red.",
            "Duración exacta en las canciones del servidor.",
            "Las canciones nuevas del teléfono aparecen al volver a la app.",
            "Más seguridad: la contraseña del servidor ya no queda en lo que se guarda.",
            "Menos batería y menos datos."
        ),
        listOf(
            "Particles when you like a song or rate it; a broken heart when you unlike it.",
            "“Close” button on the global equalizer notification.",
            "Play responds even while a song is loading, and retries by itself if the network drops.",
            "Exact duration for server songs.",
            "New songs on the phone show up when you return to the app.",
            "Safer: the server password is no longer kept in saved data.",
            "Less battery and data use."
        )
    ),
    Release(
        "2.6.5",
        listOf(
            "Vista general de la curva justo debajo del ecualizador.",
            "La frecuencia de cada punto se ajusta arrastrándolo en la gráfica."
        ),
        listOf(
            "Overview of the whole curve right below the equalizer.",
            "Each point's frequency is set by dragging it on the graph."
        )
    ),
    Release(
        "2.6.4",
        listOf(
            "Gráfica del ecualizador al doble de ancho, con deslizamiento.",
            "Guardado automático del perfil del ecualizador.",
            "Volumen extra y limitador opcional."
        ),
        listOf(
            "Equalizer graph twice as wide, with scrolling.",
            "Automatic saving of the equalizer profile.",
            "Extra volume and optional limiter."
        )
    ),
    Release(
        "2.6.3",
        listOf("Widget del teléfono: un diseño para cada tamaño, sin botones cortados y con la carátula más grande."),
        listOf("Phone widget: a layout for every size, no cut-off buttons and a bigger cover.")
    ),
    Release(
        "2.6.2",
        listOf(
            "En las canciones del servidor, lo que falta descargar se ve más tenue en la línea de reproducción.",
            "Las listas cargan de forma uniforme al deslizar rápido.",
            "Doble toque a un lado de la carátula: ahora se ve un número grande, sin bordes.",
            "Widget con carátula más grande, estrellas y más controles.",
            "Arreglos: botón de ajustes en la biblioteca y esquinas del cuadro aleatorio."
        ),
        listOf(
            "For server songs, the part not yet downloaded looks dimmer on the playback line.",
            "Lists load evenly when scrolling fast.",
            "Double tap beside the cover: now shows a big number, with no edges.",
            "Widget with a bigger cover, stars and more controls.",
            "Fixes: settings button in the library and corners of the shuffle tile."
        )
    ),
    Release(
        "2.6.1",
        listOf("Widget de Luna player para la pantalla principal del teléfono."),
        listOf("Luna player widget for the phone's home screen.")
    ),
    Release(
        "2.6.0",
        listOf(
            "14 cuadros nuevos en Inicio: Top 5 de la semana, Álbum del mes, Racha, Estantería de 5 estrellas, Por estrellas, Ahora suena bien, Mejores partes, Por colores, Línea del tiempo, Seguir escuchando, Recientes, Nuevo en Navidrome, Last.fm y Fijar una lista.",
            "Los cuadros cambian de tamaño y se acomodan solos."
        ),
        listOf(
            "14 new Home tiles: Weekly top 5, Album of the month, Streak, 5-star shelf, By stars, Sounds good now, Best parts, By colors, Timeline, Keep listening, Recent, New on Navidrome, Last.fm and Pin a playlist.",
            "Tiles can be resized and arrange themselves."
        )
    ),
    Release(
        "2.5.3",
        listOf(
            "Me gusta y estrellas para álbumes, sincronizados con el servidor.",
            "Añadir un álbum completo a la cola o a continuación."
        ),
        listOf(
            "Likes and stars for albums, synced with the server.",
            "Add a whole album to the queue or play it next."
        )
    ),
    Release(
        "2.5.2",
        listOf("Ajustes → Servidor muestra cuándo fue la última sincronización."),
        listOf("Settings → Server shows when the last sync happened.")
    ),
    Release(
        "2.5.0 – 2.5.1",
        listOf(
            "Last.fm: scrobbles, me gusta como Loved y tus estadísticas.",
            "Copia de seguridad: exporta e importa tus datos y ajustes.",
            "Biblioteca: elige ver todo, solo el teléfono o solo el servidor."
        ),
        listOf(
            "Last.fm: scrobbles, likes as Loved and your stats.",
            "Backup: export and import your data and settings.",
            "Library: choose to see everything, only the phone or only the server."
        )
    ),
    Release(
        "2.4.0 – 2.4.1",
        listOf("Listas, me gusta y estrellas iguales en todos tus dispositivos, a través de tu servidor."),
        listOf("Playlists, likes and stars kept the same on all your devices, through your server.")
    ),
    Release(
        "2.3.0",
        listOf(
            "Ecualizador global: se aplica a todo lo que suena en el dispositivo.",
            "Asigna un perfil del ecualizador a un dispositivo (auriculares, Bluetooth…) y se pone solo al conectarlo."
        ),
        listOf(
            "Global equalizer: applies to everything playing on the device.",
            "Assign an equalizer profile to a device (headphones, Bluetooth…) and it turns on by itself when connected."
        )
    ),
    Release(
        "2.2.0 – 2.2.2",
        listOf("Cuadros nuevos en Inicio: Reloj, Álbum sorpresa, Redescubre y Toda mi música al azar."),
        listOf("New Home tiles: Clock, Surprise album, Rediscover and All my music shuffled.")
    ),
    Release(
        "2.0.7 – 2.1.5",
        listOf(
            "Pantalla dividida en horizontal: navegador a la izquierda y reproductor a la derecha (activa en tablets).",
            "Mejoras de diseño, fluidez y correcciones."
        ),
        listOf(
            "Split screen in landscape: browser on the left and player on the right (on by default on tablets).",
            "Design, smoothness and bug fixes."
        )
    ),
    Release(
        "2.0.6",
        listOf("Ayuda con el botón «!» junto a cada opción."),
        listOf("Help behind the “!” button next to each option.")
    ),
    Release(
        "2.0.4 – 2.0.5",
        listOf("Edita la información de una canción: título, artista, álbum y género."),
        listOf("Edit a song's info: title, artist, album and genre.")
    ),
    Release(
        "2.0.3",
        listOf("Cuadro «Me gusta» en Inicio."),
        listOf("“Likes” tile on Home.")
    ),
    Release(
        "2.0.2",
        listOf("Añade los cuadros que quieras a Inicio."),
        listOf("Add the tiles you want to Home.")
    ),
    Release(
        "2.0.0 – 2.0.1",
        listOf("Buscador de canciones desde Inicio."),
        listOf("Song search from Home.")
    ),
    Release(
        "1.10.0",
        listOf(
            "Pantalla de Inicio editable con cuadros: Quick picks, álbumes, artistas y géneros.",
            "Fija canciones, álbumes y artistas en Inicio."
        ),
        listOf(
            "Editable Home screen with tiles: Quick picks, albums, artists and genres.",
            "Pin songs, albums and artists to Home."
        )
    ),
    Release(
        "1.9.11 – 1.9.12",
        listOf(
            "Música del teléfono y de tu servidor Navidrome/Subsonic: canciones, álbumes, artistas, carpetas y listas.",
            "Me gusta y estrellas de 0 a 5.",
            "Ecualizador paramétrico con perfiles.",
            "Slow + Reverb con ajustes guardados.",
            "Modo luna: marca la mejor parte de cada canción.",
            "Español e inglés."
        ),
        listOf(
            "Music from your phone and your Navidrome/Subsonic server: songs, albums, artists, folders and playlists.",
            "Likes and 0 to 5 stars.",
            "Parametric equalizer with profiles.",
            "Slow + Reverb with saved settings.",
            "Moon mode: mark the best part of each song.",
            "Spanish and English."
        )
    )
)

/** Pantalla de Ajustes → Novedades. */
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val accent = accentColor
    val english = Str.get(R.string.changelog_lang) == "en"
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 140.dp)
    ) {
        SettingsHeader(title = Str.get(R.string.changelog_title), onBack = onBack)
        RELEASES.forEachIndexed { index, r ->
            Spacer(Modifier.height(if (index == 0) 4.dp else 10.dp))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.version, style = MaterialTheme.typography.titleLarge, color = if (index == 0) accent else ink)
                    if (index == 0) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            Str.get(R.string.changelog_current),
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            modifier = Modifier
                                .background(accent.copy(alpha = 0.16f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                for (line in if (english) r.en else r.es) {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = accent)
                        Spacer(Modifier.width(8.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
