<div align="center">

# 🌙 Luna player

**Reproductor de música nativo para Android** — archivos locales + servidor Subsonic / Navidrome.
Minimalista, oscuro y con el fondo hecho de la carátula que suena.

*Native Android music player — local files + Subsonic / Navidrome server.*

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white)
![Media3](https://img.shields.io/badge/Media3-1.4.1-orange)
![Idiomas](https://img.shields.io/badge/idiomas-ES%20%7C%20EN-blue)

</div>

---

## ✨ Características

### Biblioteca
- Pestañas: **Álbumes · Artistas · Canciones · Listas · Me gusta · Carpetas** (teléfono y nube por separado), con búsqueda en vivo.
- **Listas de reproducción**: crear, renombrar, eliminar; añadir con pulsación larga o desde el reproductor.
- **♥ Me gusta** y **1–5 estrellas** por canción, con filtro por estrellas.
- **Arranque instantáneo**: la biblioteca (también la del servidor) va en caché y se refresca en segundo plano.
- **Retoma la última canción** al abrir la app (misma cola, canción y segundo, en pausa).

### Reproductor
- Carátula grande que **vuela desde la miniatura** y funde sus bordes con el fondo; el fondo de toda la app es la carátula, muy difuminada.
- Línea de tiempo con el **espectro real** de la canción (graves / medios / agudos). Toca o arrastra para saltar.
- Info técnica de la pista: **formato · kbps · kHz**.
- **Desliza para cambiar de canción**; mini-reproductor flotante.
- **Cola de reproducción** editable (arrastra para reordenar, «reproducir a continuación»), abierta deslizando hacia arriba, con animación fluida.
- **Aleatorio real**: empieza en una canción al azar y la cola coincide con el orden de reproducción. Precarga de las siguientes carátulas.
- **Mejor parte 🌙**: marca el momento favorito de cada canción (un toque va a él; pulsación larga lo mueve a la posición actual). Con el **modo luna** activado, cada canción nueva empieza directamente en su mejor parte.
- Segundo plano con notificación, pantalla de bloqueo y Bluetooth (`MediaSessionService`), pausa al desconectar auriculares y manejo de *audio focus*.

### Sonido
- **Ecualizador paramétrico** de **0 a 7 curvas** (campana, tonos bajo/alto, pasos, muesca) con frecuencia, ganancia (±20 dB) y Q ajustables, curva en vivo y perfiles que guardas tú.
- **Ganancia general** como barra vertical (±20 dB) junto a la gráfica; los colores **avisan en rojo** cuando se exige tanto que el sonido puede saturarse.
- **Slow + Reverb** (botón de agujero negro 🕳️: toque = activar/desactivar, pulsación larga = ajustes): velocidad, tono y reverberación en tiempo real, con tus propios ajustes guardados.
- **Off vocal**: reduce la voz principal en tiempo real, con intensidad ajustable.

### Servidor
- **Subsonic / Navidrome / Airsonic / Gonic**: la música del servidor se mezcla con la del teléfono (marcada con ☁), con **calidad de streaming** configurable. La contraseña no se guarda: solo el *token* cifrado que pide la API.

### Idioma y apariencia
- **Español (base) e inglés**. Sigue el idioma del teléfono o se elige en *Ajustes → Idioma*.
- Un único tema: oscuro, con fondo de carátula difuminada.
- Icono propio: luna menguante minimalista en azul muy oscuro.

---

## 🧱 Tecnología

| | |
|---|---|
| Lenguaje | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Audio | AndroidX **Media3** (ExoPlayer + MediaSession) con procesadores de audio propios (ecualizador, reverb, quitar voz) |
| Imágenes | Coil |
| Mínimo | Android 8.0 (API 26) |
| Build | Gradle (Kotlin DSL), AGP 8.9, compileSdk/targetSdk 36 |

Los filtros del ecualizador son *biquads* (RBJ cookbook) aplicados directamente sobre el audio PCM; la misma matemática dibuja la gráfica.

## 🚀 Cómo compilarlo

1. Instala **Android Studio** (Koala o más reciente).
2. `File ▸ Open` y elige la carpeta del repositorio.
3. Espera a que Gradle sincronice (descarga las dependencias).
4. Conecta tu teléfono (depuración USB activada) o crea un emulador con música y pulsa **Run ▶**, o usa `Build ▸ Assemble Project` para generar el APK en `app/build/outputs/apk/debug/app-debug.apk`. Para publicar: `Build ▸ Generate App Bundles` (necesitas tu propio `keystore.properties`, que **no** se sube al repo).

## 🗂️ Estructura

```
app/src/main/java/com/melodia/app/
 ├─ MainActivity.kt            permisos, navegación, mini-player y player
 ├─ MelodiaApp.kt              repositorio, ecualizador, efectos y ajustes compartidos
 ├─ Str.kt · LocaleManager.kt  textos traducidos y selector de idioma
 ├─ data/                      modelos, MediaStore, favoritos, listas, caché, servidor Subsonic
 ├─ player/                    PlaybackService y PlayerController
 ├─ audio/                     ecualizador, reverb, quitar voz, espectro
 └─ ui/
     ├─ theme/                 paleta, tipografía (Sora + Outfit), formas
     ├─ components/            carátula, filas, efectos, icono de luna
     ├─ library/               biblioteca, listas y pantallas de detalle
     ├─ player/                MiniPlayer, PlayerScreen, panel Slow + Reverb
     └─ options/               ajustes, ecualizador, servidor
app/src/main/res/
 ├─ values/strings.xml         español (base)
 └─ values-en/strings.xml      inglés
```

## 🌍 Idiomas

Los textos viven en `values/strings.xml` (español) y `values-en/strings.xml` (inglés). Para añadir otro idioma: crea `values-xx/strings.xml`, agrégalo a `res/xml/locales_config.xml` y a las opciones de *Ajustes → Idioma*.

## 📌 Estado

Proyecto personal en desarrollo activo. Versión actual: **1.9.9** (paquete `com.lunaplayer.app`). Probado en un Samsung Galaxy S26 Ultra.

## 🔒 Privacidad y publicación

- [Política de privacidad](PRIVACY.md): la app no recopila ningún dato.
- [Guía para publicar en Google Play](docs/PLAY_STORE.md).

## ☕ Apoyo

Si te gusta Luna player: https://ko-fi.com/raizzon

## 🗺️ Ideas pendientes

- Temporizador de sueño.
- Widget y Android Auto.
- Más ajustes en la pantalla del reproductor.

---

<sub>Hecho con Kotlin y mucho cariño por la música. 🌙</sub>
