package com.melodia.app.ui.options

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import com.melodia.app.R
import com.melodia.app.Str
import com.melodia.app.data.Song
import com.melodia.app.data.lastfm.LastFm
import com.melodia.app.data.lastfm.LastFmException
import com.melodia.app.data.lastfm.LfmArtistInfo
import com.melodia.app.data.lastfm.LfmItem
import com.melodia.app.data.lastfm.LfmPeriod
import com.melodia.app.data.lastfm.LfmProfile
import com.melodia.app.ui.components.Artwork
import com.melodia.app.ui.components.HelpBadge
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.library.LibraryState
import com.melodia.app.ui.theme.LocalPalette
import com.melodia.app.ui.theme.ink
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private val LfmRed = Color(0xFFD51007)

/** Ajustes → Last.fm: conectar la cuenta, scrobbles y tus estadísticas. */
@Composable
fun LastFmScreen(
    lastfm: LastFm,
    library: LibraryState,
    onPlaySong: (List<Song>, Int) -> Unit,
    onBack: () -> Unit
) {
    val user by lastfm.user.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 140.dp)
    ) {
        SettingsHeader(title = "Last.fm", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        if (user == null) LoginCard(lastfm) else ConnectedContent(lastfm, user!!, library, onPlaySong)
    }
}

// ---------------------------------------------------------------------------- inicio de sesión

@Composable
private fun LoginCard(lastfm: LastFm) {
    val accent = accentColor
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val saved = remember { lastfm.customKey() }
    var key by remember { mutableStateOf(saved.first) }
    var secret by remember { mutableStateOf(saved.second) }
    var showKey by remember { mutableStateOf(!lastfm.builtInKey || saved.first.isNotBlank()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SettingsSectionLabel(Str.get(R.string.lfm_connect), help = Str.get(R.string.lfm_login_help))
    SettingsCard {
        Field(name, { name = it }, Str.get(R.string.lfm_user))
        Spacer(Modifier.height(10.dp))
        Field(pass, { pass = it }, Str.get(R.string.lfm_pass), password = true)
        if (showKey) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Str.get(R.string.lfm_api_key_title), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.7f))
                HelpBadge(Str.get(R.string.lfm_api_key_help), Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            Field(key, { key = it }, "API key")
            Spacer(Modifier.height(10.dp))
            Field(secret, { secret = it }, "Shared secret")
            Spacer(Modifier.height(8.dp))
            Text(
                Str.get(R.string.lfm_get_key),
                style = MaterialTheme.typography.labelLarge, color = accent,
                modifier = Modifier.pressable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.last.fm/api/account/create"))) }
                }.padding(vertical = 6.dp)
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                Str.get(R.string.lfm_use_own_key),
                style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.55f),
                modifier = Modifier.pressable { showKey = true }.padding(vertical = 6.dp)
            )
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = ErrorRed)
        }
        Spacer(Modifier.height(14.dp))
        val ready = name.isNotBlank() && pass.isNotBlank() && !busy
        Box(
            Modifier
                .fillMaxWidth()
                .pressable {
                    if (!ready) return@pressable
                    if (showKey) lastfm.setCustomKey(key, secret)
                    if (!lastfm.hasKey) { error = Str.get(R.string.lfm_need_key); showKey = true; return@pressable }
                    busy = true; error = null
                    scope.launch {
                        runCatching { lastfm.login(name, pass) }.onFailure { e ->
                            error = when {
                                e is LastFmException && e.code == 4 -> Str.get(R.string.lfm_bad_login)
                                e is LastFmException && (e.code == 10 || e.code == 26 || e.code == 13) -> Str.get(R.string.lfm_bad_key)
                                else -> Str.get(R.string.lfm_error, e.message ?: "")
                            }
                        }
                        busy = false
                    }
                }
                .background(LfmRed.copy(alpha = if (ready) 1f else 0.4f), RoundedCornerShape(50))
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(Str.get(R.string.lfm_connect), style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false) {
    val accent = accentColor
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent, unfocusedBorderColor = ink.copy(alpha = 0.2f),
            focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = accent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------------------------------------------------------------------------- conectado

private enum class Tab(val res: Int) { ARTISTS(R.string.lfm_tab_artists), TRACKS(R.string.lfm_tab_tracks), ALBUMS(R.string.lfm_tab_albums), RECENT(R.string.lfm_tab_recent), LOVED(R.string.lfm_tab_loved) }

private fun periodLabel(p: LfmPeriod) = Str.get(
    when (p) {
        LfmPeriod.WEEK -> R.string.lfm_p_week
        LfmPeriod.MONTH -> R.string.lfm_p_month
        LfmPeriod.QUARTER -> R.string.lfm_p_quarter
        LfmPeriod.HALF -> R.string.lfm_p_half
        LfmPeriod.YEAR -> R.string.lfm_p_year
        LfmPeriod.ALL -> R.string.lfm_p_all
    }
)

@Composable
private fun ConnectedContent(lastfm: LastFm, user: String, library: LibraryState, onPlaySong: (List<Song>, Int) -> Unit) {
    val accent = accentColor
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrobbling by lastfm.scrobbling.collectAsState()
    val loveSync by lastfm.loveSync.collectAsState()
    val pending by lastfm.pending.collectAsState()
    var profile by remember { mutableStateOf<LfmProfile?>(null) }
    var sentLikes by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(user) { profile = runCatching { lastfm.profile() }.getOrNull() }

    // --- Perfil ---
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(profile?.image?.let(Uri::parse), 60.dp, circle = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(profile?.realName?.ifBlank { null } ?: user, style = MaterialTheme.typography.titleLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@$user" + (profile?.country?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.6f))
                profile?.registered?.takeIf { it > 0 }?.let {
                    Text(Str.get(R.string.lfm_since, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.5f))
                }
            }
        }
        profile?.let { p ->
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(num(p.playcount), Str.get(R.string.lfm_scrobbles), Modifier.weight(1f))
                Stat(num(p.artists), Str.get(R.string.lfm_tab_artists), Modifier.weight(1f))
                Stat(num(p.albums), Str.get(R.string.lfm_tab_albums), Modifier.weight(1f))
                Stat(num(p.tracks), Str.get(R.string.lfm_tab_tracks), Modifier.weight(1f))
            }
        }
    }

    // --- Opciones ---
    SettingsSectionLabel(Str.get(R.string.lfm_options))
    SettingsCard {
        ToggleRow(Str.get(R.string.lfm_scrobble), Str.get(R.string.lfm_scrobble_help), scrobbling) { lastfm.setScrobbling(it) }
        Spacer(Modifier.height(10.dp))
        ToggleRow(Str.get(R.string.lfm_love), Str.get(R.string.lfm_love_help), loveSync) { lastfm.setLoveSync(it) }
        if (pending > 0) {
            Spacer(Modifier.height(10.dp))
            Text(Str.get(R.string.lfm_pending, pending), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(sentLikes?.let { Str.get(R.string.lfm_sent_likes, it) } ?: Str.get(R.string.lfm_send_likes), Modifier.weight(1f)) {
                scope.launch { sentLikes = runCatching { lastfm.sendAllLikes() }.getOrDefault(0) }
            }
            Pill(Str.get(R.string.lfm_open_profile), Modifier.weight(1f)) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profile?.url ?: "https://www.last.fm/user/$user"))) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            Str.get(R.string.lfm_disconnect),
            style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, color = ErrorRed,
            modifier = Modifier.fillMaxWidth().pressable { lastfm.logout() }
                .glass(RoundedCornerShape(50), fill = 0.05f, border = 0.12f).padding(vertical = 12.dp)
        )
    }

    // --- Estadísticas ---
    var tab by remember { mutableStateOf(Tab.ARTISTS) }
    var period by remember { mutableStateOf(LfmPeriod.MONTH) }
    var items by remember { mutableStateOf<List<LfmItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var artistOpen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab, period, user) {
        items = null; failed = false
        val r = runCatching {
            when (tab) {
                Tab.ARTISTS -> lastfm.topArtists(period)
                Tab.TRACKS -> lastfm.topTracks(period)
                Tab.ALBUMS -> lastfm.topAlbums(period)
                Tab.RECENT -> lastfm.recent()
                Tab.LOVED -> lastfm.loved()
            }
        }
        items = r.getOrNull() ?: emptyList()
        failed = r.isFailure
    }

    SettingsSectionLabel(Str.get(R.string.lfm_stats))
    Chips(Tab.entries.map { Str.get(it.res) }, tab.ordinal) { tab = Tab.entries[it] }
    if (tab == Tab.ARTISTS || tab == Tab.TRACKS || tab == Tab.ALBUMS) {
        Spacer(Modifier.height(8.dp))
        Chips(LfmPeriod.entries.map(::periodLabel), period.ordinal) { period = LfmPeriod.entries[it] }
    }
    Spacer(Modifier.height(10.dp))

    // Imágenes y canciones de la biblioteca para lo que Last.fm no trae (las fotos de artistas ya no las da).
    val artistArt = remember(library.artists) { library.artists.associate { it.name.lowercase() to it.artUri } }
    val songByKey = remember(library.songs) { library.songs.associateBy { (it.title + "|" + it.artist).lowercase() } }
    val albumArt = remember(library.albums) { library.albums.associate { (it.title + "|" + it.artist).lowercase() to it.artUri } }

    val list = items
    when {
        list == null -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
        }
        list.isEmpty() -> Text(
            Str.get(if (failed) R.string.lfm_load_error else R.string.lfm_empty),
            style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
        )
        else -> {
            val max = list.maxOf { it.plays }.coerceAtLeast(1)
            Column(Modifier.padding(horizontal = 16.dp)) {
                list.forEachIndexed { i, it ->
                    val local = when (tab) {
                        Tab.ARTISTS -> null
                        else -> songByKey[(it.title + "|" + it.artist).lowercase()]
                    }
                    val img = it.image?.let(Uri::parse) ?: when (tab) {
                        Tab.ARTISTS -> artistArt[it.title.lowercase()]
                        Tab.ALBUMS -> albumArt[(it.title + "|" + it.artist).lowercase()]
                        else -> local?.artUri
                    }
                    StatRow(
                        rank = if (tab == Tab.RECENT || tab == Tab.LOVED) null else i + 1,
                        item = it, image = img, circle = tab == Tab.ARTISTS,
                        fraction = it.plays / max.toFloat(),
                        playable = local != null,
                        onClick = {
                            when {
                                tab == Tab.ARTISTS -> artistOpen = it.title
                                local != null -> onPlaySong(listOf(local), 0)
                                else -> artistOpen = it.artist
                            }
                        }
                    )
                }
            }
        }
    }

    artistOpen?.let { name -> ArtistDialog(lastfm, name, artistArt, onArtist = { artistOpen = it }, onDismiss = { artistOpen = null }) }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.55f), maxLines = 1)
    }
}

@Composable
private fun ToggleRow(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = accentColor
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = ink, modifier = Modifier.weight(1f))
        HelpBadge(help, Modifier.padding(horizontal = 6.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = accent, checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = ink.copy(alpha = 0.7f), uncheckedTrackColor = ink.copy(alpha = 0.15f), uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, color = ink, maxLines = 1,
        modifier = modifier.pressable(onClick).glass(RoundedCornerShape(50), fill = 0.08f, border = 0.16f).padding(vertical = 12.dp, horizontal = 8.dp)
    )
}

@Composable
private fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val accent = accentColor
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { i, l ->
            val sel = i == selected
            Text(
                l, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                color = if (sel) accent.onAccent() else ink.copy(alpha = 0.85f),
                modifier = Modifier.pressable { onSelect(i) }
                    .background(if (sel) accent else ink.copy(alpha = 0.09f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun StatRow(rank: Int?, item: LfmItem, image: Uri?, circle: Boolean, fraction: Float, playable: Boolean, onClick: () -> Unit) {
    val accent = accentColor
    Row(
        Modifier.fillMaxWidth().pressable(onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            Text(rank.toString(), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.5f), textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
            Spacer(Modifier.width(10.dp))
        }
        Artwork(image, 48.dp, cornerRadius = 10.dp, circle = circle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                if (item.artist.isNotBlank()) add(item.artist)
                if (item.nowPlaying) add(Str.get(R.string.lfm_now)) else if (item.date > 0) add(ago(item.date))
            }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelMedium, color = if (item.nowPlaying) accent else ink.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.plays > 0) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0.03f, 1f)).height(3.dp).background(accent.copy(alpha = 0.55f), RoundedCornerShape(50)))
            }
        }
        if (item.plays > 0) {
            Spacer(Modifier.width(10.dp))
            Text(num(item.plays.toLong()), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.75f))
        }
        if (playable) {
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.PlayArrow, null, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ArtistDialog(lastfm: LastFm, name: String, artistArt: Map<String, Uri?>, onArtist: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val accent = accentColor
    val context = LocalContext.current
    var info by remember(name) { mutableStateOf<LfmArtistInfo?>(null) }
    var failed by remember(name) { mutableStateOf(false) }
    LaunchedEffect(name) {
        val lang = if (Locale.getDefault().language == "es") "es" else "en"
        val r = runCatching { lastfm.artistInfo(name, lang) }
        info = r.getOrNull(); failed = r.isFailure
    }
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 560.dp)
                .background(palette.bg.copy(alpha = 0.97f), shape)
                .glass(shape, fill = 0.05f, border = 0.14f)
                .verticalScroll(rememberScrollState())
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(artistArt[name.lowercase()], 64.dp, circle = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(info?.name ?: name, style = MaterialTheme.typography.titleLarge, color = ink)
                    info?.let {
                        Text(Str.get(R.string.lfm_listeners, num(it.listeners)), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.6f))
                        if (it.userPlays > 0) Text(Str.get(R.string.lfm_your_plays, num(it.userPlays)), style = MaterialTheme.typography.labelMedium, color = accent)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            val i = info
            when {
                i == null && !failed -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent, modifier = Modifier.size(26.dp))
                }
                i == null -> Text(Str.get(R.string.lfm_load_error), color = ink.copy(alpha = 0.6f))
                else -> {
                    if (i.tags.isNotEmpty()) {
                        Text(i.tags.take(6).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f))
                        Spacer(Modifier.height(10.dp))
                    }
                    if (i.bio.isNotBlank()) {
                        Text(i.bio, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f))
                        Spacer(Modifier.height(14.dp))
                    }
                    if (i.similar.isNotEmpty()) {
                        Text(Str.get(R.string.lfm_similar), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.7f))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            i.similar.forEach { s ->
                                Column(
                                    Modifier.width(76.dp).pressable { onArtist(s.title) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Artwork(artistArt[s.title.lowercase()] ?: s.image?.let(Uri::parse), 64.dp, circle = true)
                                    Spacer(Modifier.height(4.dp))
                                    Text(s.title, style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    Text(
                        Str.get(R.string.lfm_open_lastfm), style = MaterialTheme.typography.labelLarge, color = accent,
                        modifier = Modifier.pressable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(i.url))) } }.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

private fun num(n: Long): String = NumberFormat.getIntegerInstance().format(n)

private fun ago(t: Long): String {
    val d = (System.currentTimeMillis() - t).coerceAtLeast(0) / 60_000
    return when {
        d < 1 -> Str.get(R.string.lfm_now)
        d < 60 -> Str.get(R.string.lfm_min_ago, d)
        d < 60 * 24 -> Str.get(R.string.lfm_h_ago, d / 60)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))
    }
}
