# Reglas ProGuard/R8 (Media3, Compose y Coil incluyen las suyas)

# Procesadores de audio propios y servicio de reproducción: se crean por referencia directa,
# pero se conservan por seguridad.
-keep class com.melodia.app.audio.** { *; }
-keep class com.melodia.app.player.** { *; }

# Enumeraciones que se guardan por nombre (tipos de filtro, etc.).
-keepclassmembers enum com.melodia.app.** { *; }

# Avisos de librerías opcionales.
-dontwarn org.jetbrains.annotations.**
