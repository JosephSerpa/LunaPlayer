package com.melodia.app.data.server

import android.net.Uri

/**
 * Credenciales del servidor, añadidas solo en el momento de cada descarga.
 *
 * Las direcciones que se guardan (base de datos, widget, cola) o que ven otras apps (la sesión de
 * reproducción, la notificación) van SIN usuario ni token: así nadie puede sacar de ellas el acceso
 * al servidor. Coil, el reproductor y el análisis del espectro las firman con [sign] justo antes de pedirlas.
 */
object ServerAuth {

    @Volatile private var config: ServerConfig = ServerConfig()

    /** Parámetros que llevan las credenciales (y la versión/cliente que pide la API). */
    private val AUTH_PARAMS = setOf("u", "t", "s", "p", "v", "c", "f")

    fun update(c: ServerConfig) { config = c }

    /** Añade usuario y token a una dirección del servidor configurado que no los tenga. */
    fun sign(uri: Uri): Uri {
        val c = config
        if (!c.isConfigured) return uri
        if (uri.scheme != "http" && uri.scheme != "https") return uri
        if (uri.path?.contains("/rest/") != true) return uri
        if (uri.getQueryParameter("t") != null || uri.getQueryParameter("p") != null) return uri
        val base = runCatching { Uri.parse(c.url) }.getOrNull() ?: return uri
        if (!uri.authority.equals(base.authority, ignoreCase = true)) return uri
        return uri.buildUpon()
            .appendQueryParameter("u", c.user)
            .appendQueryParameter("t", c.token)
            .appendQueryParameter("s", c.salt)
            .appendQueryParameter("v", SubsonicClient.API_VERSION)
            .appendQueryParameter("c", SubsonicClient.CLIENT)
            .build()
    }

    fun sign(url: String): String = sign(Uri.parse(url)).toString()

    /** Quita las credenciales de una dirección (para migrar lo guardado por versiones anteriores). */
    fun strip(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        if ((uri.scheme != "http" && uri.scheme != "https") || uri.getQueryParameter("t") == null) return url
        val b = uri.buildUpon().clearQuery()
        for (name in uri.queryParameterNames) {
            if (name in AUTH_PARAMS) continue
            for (v in uri.getQueryParameters(name)) b.appendQueryParameter(name, v)
        }
        return b.build().toString()
    }
}

/**
 * Coil: firma las carátulas del servidor al descargarlas. La copia en disco se guarda con la dirección
 * sin credenciales, así cambiar la contraseña no obliga a bajar todas las carátulas otra vez.
 */
class ServerAuthInterceptor : coil.intercept.Interceptor {
    override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
        val req = chain.request
        val uri = when (val d = req.data) {
            is Uri -> d
            is String -> if (d.startsWith("http")) Uri.parse(d) else null
            else -> null
        } ?: return chain.proceed(req)
        val signed = ServerAuth.sign(uri)
        if (signed == uri) return chain.proceed(req)
        return chain.proceed(req.newBuilder().data(signed).diskCacheKey(uri.toString()).build())
    }
}
