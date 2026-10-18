# Publicar Luna player en Google Play

Paquete: `com.lunaplayer.app` · Versión 1.9.9 (código 104) · Min. Android 8.0 · Target Android 16 (API 36)

## 1. Archivos que ya tienes
| Qué | Dónde |
|---|---|
| App Bundle para Play (.aab) | `LunaPlayer-1.9.9-play.aab` |
| APK para GitHub / instalar a mano | `LunaPlayer-1.9.9-release.apk` |
| Icono 512×512 | `store/icon-512.png` |
| Gráfico de función 1024×500 | `store/feature-graphic.png` |
| Política de privacidad | `PRIVACY.md` (enlace público: https://github.com/JosephSerpa/LunaPlayer/blob/main/PRIVACY.md) |
| Llave de firma | `LunaPlayer-llave/` — **cópiala a un lugar seguro; sin ella no podrás actualizar** |

Capturas de pantalla: haz 4–8 desde tu teléfono (vertical, mínimo 2). Sugeridas: biblioteca, reproductor, cola, ecualizador, Slow + Reverb.

## 2. Textos de la ficha

### Español (idioma principal)
**Nombre (máx. 30):** Luna player

**Descripción breve (máx. 80):** Reproductor minimalista con ecualizador, slow + reverb y servidor propio

**Descripción completa:**
Luna player es un reproductor de música oscuro, minimalista y fluido. El fondo de toda la app es la carátula de lo que suena, muy difuminada.

• Tu música del teléfono y, si quieres, de tu servidor Subsonic / Navidrome, todo junto.
• Biblioteca por álbumes, artistas, canciones, listas, me gusta y carpetas (teléfono y nube por separado).
• Ecualizador paramétrico de hasta 7 curvas, ±20 dB, con perfiles que guardas tú.
• Slow + Reverb en tiempo real: velocidad, tono y reverberación con ajustes propios.
• Off vocal: reduce la voz principal.
• Línea de tiempo con el espectro real de la canción y formato / kbps.
• "Mejor parte" 🌙: marca tu momento favorito y activa el modo luna para empezar siempre ahí.
• Me gusta, de 1 a 5 estrellas, listas de reproducción y cola editable (arrastra para reordenar).
• Aleatorio de verdad: empieza en una canción al azar.
• Español e inglés.
• Sin anuncios, sin cuentas, sin rastreo.

Hecho con cariño por la música. Si te gusta, puedes invitarme un café en Ko-fi.

### English
**Name:** Luna player
**Short description:** Minimal music player with equalizer, slow + reverb and your own server
**Full description:**
Luna player is a dark, minimalist, fluid music player. The whole app background is the artwork of what’s playing, softly blurred.

• Your phone’s music and, if you want, your Subsonic / Navidrome server, all together.
• Library by albums, artists, songs, playlists, liked and folders (phone and cloud separated).
• Parametric equalizer with up to 7 curves, ±20 dB, with profiles you save yourself.
• Real-time Slow + Reverb: speed, pitch and reverb with your own saved settings.
• Off vocal: reduces the lead vocal.
• Timeline with the song’s real spectrum plus format / kbps.
• “Best part” 🌙: mark your favorite moment and turn on moon mode to always start there.
• Likes, 1–5 stars, playlists and an editable queue (drag to reorder).
• True shuffle: starts on a random song.
• Spanish and English.
• No ads, no accounts, no tracking.

Made with love for music. If you like it, you can buy me a coffee on Ko-fi.

## 3. Respuestas para los formularios de Play Console
- **Categoría:** Música y audio · **Tipo:** Aplicación · **Gratuita**.
- **Contenido de la app → Política de privacidad:** enlace de arriba.
- **Anuncios:** No contiene anuncios.
- **Acceso a la app:** todo es accesible sin cuenta (no hay inicio de sesión de la app; el servidor es opcional).
- **Público objetivo:** 18 años o más (evita el programa "Familias"). No atrae a menores.
- **Clasificación de contenido (cuestionario IARC):** utilidad / reproductor; sin violencia, sexo, drogas, apuestas ni contenido generado por usuarios → saldrá apta para todos.
- **Seguridad de los datos:** "No recopila ni comparte datos". No marques cifrado en tránsito: la app permite servidores `http` del propio usuario.
- **Servicio en primer plano (tipo `mediaPlayback`):** declara que se usa para reproducir música con la pantalla apagada y los controles en la notificación. Play puede pedir un vídeo corto: grábalo con la pantalla abierta, dando play y bloqueando el teléfono.
- **Permiso de audio (`READ_MEDIA_AUDIO`):** es la función principal (reproducir tu música local).
- **Tráfico sin cifrar (`usesCleartextTraffic`):** se mantiene para servidores caseros sin HTTPS. Si Play lo cuestiona, explica que es solo para el servidor que el propio usuario configura.

## 4. Pasos
1. **Cuenta de desarrollador:** https://play.google.com/console → pago único (~25 USD) y verificación de identidad (documento y a veces dirección). Puede tardar días.
2. **Crear app:** *Crear app* → nombre `Luna player`, idioma Español, App, Gratuita, acepta las declaraciones.
3. **Panel → Configura tu app:** completa política de privacidad, anuncios, acceso a la app, clasificación de contenido, público objetivo, seguridad de los datos y declaraciones (ver sección 3).
4. **Ficha de la tienda principal:** textos de la sección 2, icono 512, gráfico 1024×500 y capturas. Añade la traducción al inglés en *Gestionar traducciones*.
5. **Firma de apps:** al subir el primer .aab, Play te ofrece **Play App Signing**. Acepta: tu llave actual actúa como *llave de subida* y Google guarda la de distribución.
6. **Prueba cerrada (si tu cuenta es personal y nueva):** Play exige una prueba cerrada con un mínimo de testers (hoy, 12) durante 14 días seguidos antes de poder pedir producción. Confirma las cifras vigentes en Play Console. Crea la pista *Pruebas cerradas*, sube el .aab, añade los correos de los testers y comparte el enlace de aceptación.
7. **Producción:** pasados los 14 días, *Producción → Crear versión* con el mismo .aab (o uno nuevo) → *Revisar* → *Enviar a revisión*. La primera revisión puede tardar hasta unos días.
8. **Actualizaciones futuras:** sube un .aab con `versionCode` mayor (105, 106…) firmado con la misma llave.

## 5. Cosas a tener en cuenta
- `com.lunaplayer.app` es un paquete **distinto** del anterior (`com.melodia.app`): se instala como app aparte y **no hereda** listas, me gusta ni ajustes. El nombre de paquete no se puede cambiar una vez publicada.
- Guarda siempre la carpeta de la llave. Si la pierdes, con Play App Signing puedes pedir a Google un restablecimiento de la llave de subida.
- Target API: Play exige apuntar a Android 16 (API 36) desde el 31 de agosto de 2026; el proyecto ya lo hace.
