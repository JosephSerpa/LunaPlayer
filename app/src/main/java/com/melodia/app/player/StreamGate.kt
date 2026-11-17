package com.melodia.app.player

/**
 * Semáforo de la red: indica si la canción de la nube que suena tiene suficiente colchón descargado.
 * Las descargas secundarias (la copia ligera para dibujar la onda) se detienen mientras está en rojo,
 * para no quitarle ancho de banda al audio y evitar cortes.
 *
 * Lo actualiza [PlayerController] en su bucle de progreso, con histéresis: pasa a rojo con menos de
 * 12 s por delante y vuelve a verde con 25 s o con la canción ya completa.
 */
object StreamGate {
    @Volatile
    var healthy: Boolean = true
        private set

    /** Salto o carga en curso: el audio necesita toda la conexión ya, sin esperar al siguiente repaso. */
    fun block() { healthy = false }

    fun update(remote: Boolean, headroomMs: Long, complete: Boolean) {
        healthy = when {
            !remote || complete -> true
            healthy -> headroomMs >= 12_000
            else -> headroomMs >= 25_000
        }
    }
}
