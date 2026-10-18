package com.melodia.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.melodia.app.data.Playlist
import com.melodia.app.data.Song
import com.melodia.app.data.SongMark
import com.melodia.app.data.UNKNOWN_ARTIST
import com.melodia.app.ui.components.AmbientBackground
import com.melodia.app.ui.components.ArtColors
import com.melodia.app.ui.components.CoverOrigin
import com.melodia.app.ui.components.LocalCoverOrigin
import com.melodia.app.ui.components.LocalMarks
import com.melodia.app.ui.components.LocalSongMenu
import com.melodia.app.ui.components.LocalCollectionMenu
import com.melodia.app.ui.components.CollectionTarget
import com.melodia.app.ui.library.CollectionMenuDialog
import com.melodia.app.ui.player.QueueOverlay
import com.melodia.app.ui.library.PlaylistActions
import com.melodia.app.ui.library.PlaylistNameDialog
import com.melodia.app.ui.library.SongMenuDialog
import com.melodia.app.ui.components.LocalArt
import com.melodia.app.ui.components.LocalShownArt
import com.melodia.app.ui.components.prepareArt
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.library.DetailScreen
import com.melodia.app.ui.library.LibraryScreen
import com.melodia.app.ui.library.LibraryViewModel
import com.melodia.app.ui.options.EqualizerScreen
import com.melodia.app.ui.options.ServerScreen
import com.melodia.app.ui.options.SettingsScreen
import com.melodia.app.ui.player.MiniPlayer
import com.melodia.app.ui.player.PlayerScreen
import com.melodia.app.ui.theme.LocalPalette
import com.melodia.app.ui.theme.MelodiaTheme
import com.melodia.app.ui.theme.ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.melodia.app.ui.theme.inverse

class MainActivity : ComponentActivity() {

    /** Cada vez que se toca la notificación sube en 1: la interfaz abre el reproductor. */
    private val openPlayerRequests = MutableStateFlow(0)

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_PLAYER) openPlayerRequests.value += 1
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) handleIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            MelodiaTheme(dark = true) {
                Box(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
                    PermissionGate { MelodiaRoot(openPlayerRequests) }
                }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.melodia.app.OPEN_PLAYER"
    }
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission()) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = result[audioPermission()] == true }

    if (granted) {
        content()
    } else {
        Box(Modifier.fillMaxSize()) {
            AmbientBackground(artUri = null, colors = ArtColors(), isPlaying = true)
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Luna player", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(14.dp))
                Text(
                    Str.get(R.string.perm_text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                Text(
                    Str.get(R.string.perm_button),
                    style = MaterialTheme.typography.titleMedium,
                    color = inverse,
                    modifier = Modifier
                        .pressable { launcher.launch(requiredPermissions()) }
                        .background(ink, RoundedCornerShape(50))
                        .padding(horizontal = 32.dp, vertical = 15.dp)
                )
            }
        }
    }
}

@Composable
private fun MelodiaRoot(openPlayerRequests: StateFlow<Int>) {
    val context = LocalContext.current
    val app = context.applicationContext as MelodiaApp
    val controller = app.playerController
    val vm: LibraryViewModel = viewModel()
    val library by vm.state.collectAsStateWithLifecycle()
    val player by controller.state.collectAsStateWithLifecycle()
    val equalizer = app.equalizer
    val eqSettings by equalizer.settings.collectAsStateWithLifecycle()
    val serverConfig by app.serverSettings.config.collectAsStateWithLifecycle()
    val marks by app.favorites.marks.collectAsStateWithLifecycle()
    val fx by app.effects.settings.collectAsStateWithLifecycle()
    val moonMode by app.favorites.moonMode.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val coverOrigin = remember { CoverOrigin() }
    val playlists by app.playlists.playlists.collectAsStateWithLifecycle()

    // Menú de canción (mantener presionada / botón del reproductor) y diálogos de listas.
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var menuPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    val openSongMenu: (Song) -> Unit = { menuSong = it; menuPlaylist = null }
    var menuCollection by remember { mutableStateOf<CollectionTarget?>(null) }
    val queue by controller.queue.collectAsStateWithLifecycle()
    val toast: (Int) -> Unit = { res ->
        android.widget.Toast.makeText(app, Str.get(res), android.widget.Toast.LENGTH_SHORT).show()
    }

    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    // Tocar la notificación: abre el reproductor (desde la barra inferior).
    val openRequests by openPlayerRequests.collectAsStateWithLifecycle()
    var handledRequests by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(openRequests) {
        if (openRequests > handledRequests) {
            handledRequests = openRequests
            coverOrigin.openedFromMini = true
            expanded = true
        }
    }

    // Al abrir la app se retoma la última canción (en pausa), en cuanto hay biblioteca.
    var restored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(library.songs.isNotEmpty()) {
        if (!restored && library.songs.isNotEmpty()) {
            restored = true
            controller.restoreLastSession(library.songs)
        }
    }

    // Colores de la carátula actual: alimentan el fondo y los acentos de toda la app.
    val trackArt = player.current?.artUri
    var shownArt by remember { mutableStateOf<android.net.Uri?>(null) }
    var art by remember { mutableStateOf(ArtColors()) }
    LaunchedEffect(trackArt) {
        if (trackArt == null) {
            shownArt = null
            art = ArtColors()
        } else {
            // Todo se prepara antes de cambiar nada: portada, colores y fondo cambian a la vez.
            // Si se salta de canción antes de terminar, esta tarea se cancela y NO publica nada
            // (antes terminaba igual y ponía la carátula de una canción que ya no sonaba).
            val density = context.resources.displayMetrics.density
            val colors = prepareArt(context, trackArt, listOf(1080, (50 * density).toInt()))
            ensureActive()
            art = colors ?: ArtColors()
            shownArt = trackArt
        }
    }
    // Precarga: las 2 canciones que siguen en la cola ya tienen su carátula y colores listos
    // cuando llegan (navegación fluida). Espera a que la actual esté lista para no competir con ella.
    val upcomingArt = remember(queue, player.queueIndex) {
        listOfNotNull(
            queue.getOrNull(player.queueIndex + 1)?.artUri,
            queue.getOrNull(player.queueIndex + 2)?.artUri
        ).distinct()
    }
    LaunchedEffect(upcomingArt, shownArt) {
        if (shownArt == null || shownArt != trackArt) return@LaunchedEffect
        kotlinx.coroutines.delay(350)
        val density = context.resources.displayMetrics.density
        for (u in upcomingArt) {
            prepareArt(context, u, listOf(1080, (50 * density).toInt()))
        }
    }
    val artUri = shownArt

    // 0 = reproductor cerrado, 1 = abierto. Mueve la carátula y todo lo demás.
    val playerOpen = expanded && player.current != null
    val progressAnim = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    val skipNextEffect = remember { booleanArrayOf(false) }
    val uiScope = rememberCoroutineScope()
    // Resorte suave y sin rebote: la hoja se asienta en lugar de "saltar".
    val sheetSpring = spring<Float>(dampingRatio = 0.88f, stiffness = 240f)
    LaunchedEffect(playerOpen) {
        if (skipNextEffect[0]) {
            skipNextEffect[0] = false
        } else if (!dragging) {
            progressAnim.animateTo(if (playerOpen) 1f else 0f, sheetSpring)
        }
    }
    // Estos dos solo cambian de valor al cruzar el umbral: no se redibuja nada con cada cuadro.
    val playerVisible by remember { derivedStateOf { progressAnim.value > 0.001f } }
    val miniVisible by remember { derivedStateOf { progressAnim.value < 0.02f } }

    // Cola de reproducción: 0 = oculta, 1 = abierta (el reproductor queda arriba en miniatura).
    val queueAnim = remember { Animatable(0f) }
    val queueSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)
    val openQueue: () -> Unit = { uiScope.launch { queueAnim.animateTo(1f, queueSpring) } }
    val closeQueue: () -> Unit = { uiScope.launch { queueAnim.animateTo(0f, queueSpring) } }
    LaunchedEffect(expanded) { if (!expanded) queueAnim.snapTo(0f) }
    androidx.activity.compose.BackHandler(enabled = queueAnim.value > 0.02f) { closeQueue() }
    // Gesto vertical único: hacia arriba abre la cola (el reproductor se encoge arriba como mini),
    // hacia abajo la cierra y, con la cola cerrada, cierra el reproductor. Todo sigue al dedo.
    var screenH by remember { mutableStateOf(1f) }
    var gesture by remember { mutableStateOf(0) } // 0 = ninguno, 1 = cola, 2 = cerrando reproductor
    val onDragProgress: (Float) -> Unit = { dy ->
        if (gesture == 0) gesture = if (queueAnim.value > 0.001f || dy < 0f) 1 else 2
        val g = gesture
        uiScope.launch {
            if (g == 1) {
                queueAnim.snapTo((queueAnim.value - dy / (screenH * 0.62f)).coerceIn(0f, 1f))
            } else {
                dragging = true
                progressAnim.snapTo((progressAnim.value - dy / (screenH * 0.9f)).coerceIn(0f, 1f))
            }
        }
    }
    val onDragRelease: (Float) -> Unit = { vy ->
        val g = gesture
        gesture = 0
        uiScope.launch {
            if (g == 1) {
                val v = -vy / (screenH * 0.62f) // progreso por segundo (positivo = abriendo)
                val open = v > 1.0f || (queueAnim.value > 0.4f && v > -1.0f)
                queueAnim.animateTo(if (open) 1f else 0f, queueSpring, initialVelocity = v)
            } else if (g == 2) {
                if (vy > 1600f || progressAnim.value < 0.75f) {
                    dragging = false
                    expanded = false
                } else {
                    progressAnim.animateTo(1f, sheetSpring)
                    dragging = false
                }
            }
        }
    }
    val onQueueDelta = onDragProgress
    val onQueueRelease = onDragRelease
    // Tocar una canción: suena y el reproductor se abre desde su carátula.
    val playSong: (List<Song>, Int) -> Unit = { songs, index ->
        controller.play(songs, index, false)
        expanded = true
    }

    CompositionLocalProvider(
        LocalArt provides art,
        LocalShownArt provides shownArt,
        LocalMarks provides marks,
        LocalCoverOrigin provides coverOrigin,
        LocalSongMenu provides openSongMenu,
        LocalCollectionMenu provides { target -> menuCollection = target }
    ) {
        Box(Modifier.fillMaxSize().onSizeChanged { screenH = it.height.toFloat().coerceAtLeast(1f) }) {
            // Fondo global: la carátula de la canción, muy difuminada.
            AmbientBackground(artUri = artUri, colors = art, isPlaying = player.isPlaying)

            // La biblioteca se desvanece mientras el reproductor se abre (se lee al dibujar, sin recomponer).
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - progressAnim.value }) {
                NavHost(
                    navController = nav,
                    startDestination = "library",
                    enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it / 6 } },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(300)) },
                    popExitTransition = { fadeOut(tween(250)) + slideOutHorizontally(tween(350)) { it / 6 } }
                ) {
                    composable("library") {
                        LibraryScreen(
                            state = library,
                            query = query,
                            onQueryChange = { query = it },
                            current = player.current,
                            isPlaying = player.isPlaying,
                            onArtist = { nav.navigate("artist/${android.net.Uri.encode(it)}") },
                            onAlbum = { nav.navigate("album/$it") },
                            onFolder = { nav.navigate("folder/$it") },
                            onOpenOptions = { nav.navigate("options") { launchSingleTop = true } },
                            onPlay = { songs, index, shuffle -> controller.play(songs, index, shuffle) },
                            onPlaySong = playSong,
                            playlists = playlists,
                            onPlaylist = { nav.navigate("playlist/$it") },
                            onCreatePlaylist = { creatingPlaylist = true }
                        )
                    }
                    composable(
                        "playlist/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType })
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: -1L
                        val playlist = playlists.firstOrNull { it.id == id }
                        if (playlist != null) {
                            val byId = remember(library.songs) { library.songs.associateBy { it.id } }
                            val songs = remember(playlist, byId) { playlist.songIds.mapNotNull { byId[it] } }
                            var renaming by remember { mutableStateOf(false) }
                            // Aquí, mantener presionada una canción permite quitarla de la lista.
                            CompositionLocalProvider(
                                LocalSongMenu provides { song -> menuSong = song; menuPlaylist = playlist }
                            ) {
                                DetailScreen(
                                    title = playlist.name,
                                    subtitle = Str.plural(R.plurals.songs_count, songs.size),
                                    artUri = songs.firstOrNull()?.artUri,
                                    circleArt = false,
                                    songs = songs,
                                    albums = emptyList(),
                                    current = player.current,
                                    isPlaying = player.isPlaying,
                                    onBack = { nav.popBackStack() },
                                    onAlbum = {},
                                    onPlay = { list, index, shuffle -> controller.play(list, index, shuffle) },
                                    onPlaySong = playSong,
                                    emptyText = Str.get(R.string.playlist_empty),
                                    actions = {
                                        PlaylistActions(
                                            onRename = { renaming = true },
                                            onDelete = {
                                                nav.popBackStack()
                                                app.playlists.delete(playlist.id)
                                            }
                                        )
                                    }
                                )
                            }
                            if (renaming) {
                                PlaylistNameDialog(
                                    title = Str.get(R.string.rename_playlist),
                                    initial = playlist.name,
                                    confirm = Str.get(R.string.save),
                                    onConfirm = { app.playlists.rename(playlist.id, it) },
                                    onDismiss = { renaming = false }
                                )
                            }
                        }
                    }
                    composable("options") {
                        val activity = LocalContext.current as? android.app.Activity
                        var language by remember { mutableStateOf(LocaleManager.saved(app)) }
                        SettingsScreen(
                            language = language,
                            onLanguage = { code ->
                                language = code
                                LocaleManager.save(app, code)
                                Str.init(LocaleManager.wrap(app))
                                activity?.recreate()
                            },
                            eq = eqSettings,
                            server = serverConfig,
                            library = library,
                            onOpenEqualizer = { nav.navigate("options/eq") { launchSingleTop = true } },
                            onOpenServer = { nav.navigate("options/server") { launchSingleTop = true } },
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("options/eq") {
                        EqualizerScreen(
                            settings = eqSettings,
                            eq = equalizer,
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("options/server") {
                        ServerScreen(
                            config = serverConfig,
                            library = library,
                            settings = app.serverSettings,
                            onSync = vm::syncServer,
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable(
                        "artist/{name}",
                        arguments = listOf(navArgument("name") { type = NavType.StringType })
                    ) { entry ->
                        val name = entry.arguments?.getString("name").orEmpty()
                        val artist = library.artists.firstOrNull { it.name == name }
                        if (artist != null) {
                            DetailScreen(
                                title = artist.name,
                                subtitle = "${Str.plural(R.plurals.songs_count, artist.songs.size)} · ${Str.plural(R.plurals.albums_count, artist.albums.size)}",
                                artUri = artist.artUri,
                                circleArt = true,
                                songs = artist.songs,
                                albums = artist.albums,
                                current = player.current,
                                isPlaying = player.isPlaying,
                                onBack = { nav.popBackStack() },
                                onAlbum = { nav.navigate("album/$it") },
                                onPlay = { songs, index, shuffle -> controller.play(songs, index, shuffle) },
                                onPlaySong = playSong
                            )
                        }
                    }
                    composable(
                        "folder/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.IntType })
                    ) { entry ->
                        val id = entry.arguments?.getInt("id") ?: 0
                        val folder = library.folders.firstOrNull { it.id == id }
                        if (folder != null) {
                            DetailScreen(
                                title = folder.name,
                                subtitle = "${Str.plural(R.plurals.songs_count, folder.songs.size)} · ${folder.displayPath}",
                                artUri = folder.artUri,
                                circleArt = false,
                                songs = folder.songs,
                                albums = emptyList(),
                                current = player.current,
                                isPlaying = player.isPlaying,
                                onBack = { nav.popBackStack() },
                                onAlbum = {},
                                onPlay = { songs, index, shuffle -> controller.play(songs, index, shuffle) },
                                onPlaySong = playSong
                            )
                        }
                    }
                    composable(
                        "album/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType })
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: -1L
                        val album = library.albums.firstOrNull { it.id == id }
                        if (album != null) {
                            DetailScreen(
                                title = album.title,
                                subtitle = if (album.artist == UNKNOWN_ARTIST) "${Str.plural(R.plurals.songs_count, album.songs.size)}"
                                else "${album.artist} · ${Str.plural(R.plurals.songs_count, album.songs.size)}",
                                artUri = album.artUri,
                                circleArt = false,
                                songs = album.songs,
                                albums = emptyList(),
                                current = player.current,
                                isPlaying = player.isPlaying,
                                onBack = { nav.popBackStack() },
                                onAlbum = {},
                                onPlay = { songs, index, shuffle -> controller.play(songs, index, shuffle) },
                                onPlaySong = playSong
                            )
                        }
                    }
                }
            }

            // Mini-reproductor flotante: aparece cuando la carátula ya volvió a su sitio.
            AnimatedVisibility(
                // Sin animación propia: la hoja del reproductor ya llegó exactamente a su sitio.
                visible = player.current != null && !expanded && miniVisible,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                enter = EnterTransition.None,
                exit = ExitTransition.None
            ) {
                MiniPlayer(
                    state = player,
                    position = controller.position,
                    onExpand = { expanded = true },
                    onPlayPause = controller::togglePlayPause,
                    onNext = controller::next
                )
            }

            // Reproductor a pantalla completa.
            if (playerVisible) {
                val songId = player.current?.mediaId?.toLongOrNull()
                // Solo este bloque se redibuja con cada cuadro de la animación del reproductor.
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        // Al abrir la cola, el reproductor se encoge hacia arriba y se funde en el mini reproductor.
                        val q = queueAnim.value
                        alpha = (1f - q * 1.7f).coerceIn(0f, 1f)
                        val sc = 1f - 0.45f * q
                        scaleX = sc; scaleY = sc
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.08f)
                        translationY = -q * size.height * 0.05f
                    }
                ) {
                ProgressHost(progressAnim.asState()) { playerProgress ->
                PlayerScreen(
                    state = player,
                    position = controller.position,
                    progress = playerProgress,
                    closing = !expanded,
                    collapseToMini = !expanded || dragging || coverOrigin.openedFromMini,
                    onDragProgress = onDragProgress,
                    onDragRelease = onDragRelease,
                    origin = coverOrigin,
                    mark = songId?.let { marks[it] } ?: SongMark(),
                    fx = fx,
                    effects = app.effects,
                    moonMode = moonMode,
                    onToggleMoon = { app.favorites.setMoonMode(!moonMode) },
                    onToggleLike = { songId?.let(app.favorites::toggleLike) },
                    onAddToPlaylist = {
                        library.songs.firstOrNull { it.id == songId }?.let(openSongMenu)
                    },
                    onOpenQueue = openQueue,
                    onRate = { stars -> songId?.let { app.favorites.setRating(it, stars) } },
                    onClose = { expanded = false },
                    onPlayPause = controller::togglePlayPause,
                    onNext = controller::next,
                    onPrevious = controller::previous,
                    onPreviousTrack = controller::previousTrack,
                    onSetBest = { pos -> songId?.let { app.favorites.setBest(it, pos) } },
                    onClearBest = { songId?.let(app.favorites::clearBest) },
                    onSeek = controller::seekTo,
                    onShuffle = controller::toggleShuffle,
                    onRepeat = controller::cycleRepeat,
                    onOpenOptions = {
                        expanded = false
                        nav.navigate("options/eq") { launchSingleTop = true }
                    },
                    onOpenArtist = {
                        val name = library.songs.firstOrNull { it.id == songId }?.artist
                            ?: player.current?.artist.orEmpty()
                        if (name.isNotBlank()) {
                            expanded = false
                            nav.navigate("artist/${android.net.Uri.encode(name)}") { launchSingleTop = true }
                        }
                    },
                    onOpenAlbum = {
                        val song = library.songs.firstOrNull { it.id == songId }
                        if (song != null) {
                            expanded = false
                            nav.navigate("album/${song.albumId}") { launchSingleTop = true }
                        }
                    }
                )
                }
                }
            }

            // --- Menú de canción y nueva lista ---
            menuSong?.let { song ->
                SongMenuDialog(
                    song = song,
                    playlists = playlists,
                    inPlaylist = menuPlaylist,
                    mark = marks[song.id] ?: SongMark(),
                    onPlayNext = { controller.playNext(listOf(song)); toast(R.string.toast_next) },
                    onAddToQueue = { controller.addToQueue(listOf(song)); toast(R.string.toast_queued) },
                    onToggleLike = { app.favorites.toggleLike(song.id) },
                    onRate = { stars -> app.favorites.setRating(song.id, stars) },
                    onAddTo = { pid -> app.playlists.add(pid, listOf(song.id)) },
                    onCreateWith = { name -> app.playlists.create(name, listOf(song.id)) },
                    onRemoveFromPlaylist = { menuPlaylist?.let { app.playlists.remove(it.id, song.id) } },
                    onDismiss = { menuSong = null; menuPlaylist = null }
                )
            }
            menuCollection?.let { target ->
                CollectionMenuDialog(
                    target = target,
                    playlists = playlists,
                    onPlay = { shuffle ->
                        controller.play(target.songs, 0, shuffle)
                    },
                    onPlayNext = { controller.playNext(target.songs); toast(R.string.toast_next) },
                    onAddToQueue = { controller.addToQueue(target.songs); toast(R.string.toast_queued) },
                    onAddTo = { pid -> app.playlists.add(pid, target.songs.map { it.id }) },
                    onCreateWith = { name -> app.playlists.create(name, target.songs.map { it.id }) },
                    onDismiss = { menuCollection = null }
                )
            }
            if (queueAnim.value > 0.001f) {
                QueueOverlay(
                    progress = queueAnim.asState(),
                    queue = queue,
                    state = player,
                    onJump = controller::skipToQueueItem,
                    onMove = controller::moveQueueItem,
                    onRemove = controller::removeQueueItem,
                    onPlayPause = controller::togglePlayPause,
                    onNext = controller::next,
                    onClose = closeQueue,
                    onProgressDelta = onQueueDelta,
                    onRelease = onQueueRelease
                )
            }
            if (creatingPlaylist) {
                PlaylistNameDialog(
                    title = Str.get(R.string.new_playlist),
                    initial = "",
                    confirm = Str.get(R.string.create),
                    onConfirm = { name -> nav.navigate("playlist/${app.playlists.create(name)}") },
                    onDismiss = { creatingPlaylist = false }
                )
            }
        }
    }
}

/** Lee el progreso de la animación aquí dentro: así solo este bloque se recompone con cada cuadro. */
@Composable
private fun ProgressHost(progress: androidx.compose.runtime.State<Float>, content: @Composable (Float) -> Unit) {
    content(progress.value)
}
