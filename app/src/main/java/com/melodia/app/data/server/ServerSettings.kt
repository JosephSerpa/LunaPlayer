package com.melodia.app.data.server

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Datos de conexión a un servidor Subsonic / Navidrome.
 * La contraseña nunca se guarda: solo el "token" = md5(contraseña + sal),
 * que es lo que pide la API de Subsonic.
 */
data class ServerConfig(
    val url: String = "",
    val user: String = "",
    val salt: String = "",
    val token: String = "",
    /** Mostrar la música del servidor en la biblioteca. */
    val enabled: Boolean = true,
    /** 0 = calidad original; si no, kbps máximos (el servidor convierte a MP3). */
    val maxBitRate: Int = 0
) {
    val isConfigured: Boolean get() = url.isNotBlank() && user.isNotBlank() && token.isNotBlank()
    val isActive: Boolean get() = isConfigured && enabled
}

class ServerSettings(context: Context) {

    private val prefs = context.getSharedPreferences("server", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    /** Guarda la conexión. Si [password] está vacío se conserva el token anterior. */
    fun save(url: String, user: String, password: String) {
        val old = _config.value
        val auth = if (password.isNotEmpty()) makeAuth(password) else old.salt to old.token
        store(old.copy(url = normalizeUrl(url), user = user.trim(), salt = auth.first, token = auth.second))
    }

    fun setEnabled(enabled: Boolean) = store(_config.value.copy(enabled = enabled))

    fun setMaxBitRate(kbps: Int) = store(_config.value.copy(maxBitRate = kbps))

    fun clear() = store(ServerConfig())

    private fun store(c: ServerConfig) {
        _config.value = c
        prefs.edit()
            .putString("url", c.url).putString("user", c.user)
            .putString("salt", c.salt).putString("token", c.token)
            .putBoolean("enabled", c.enabled).putInt("maxBitRate", c.maxBitRate)
            .apply()
    }

    private fun load() = ServerConfig(
        url = prefs.getString("url", "").orEmpty(),
        user = prefs.getString("user", "").orEmpty(),
        salt = prefs.getString("salt", "").orEmpty(),
        token = prefs.getString("token", "").orEmpty(),
        enabled = prefs.getBoolean("enabled", true),
        maxBitRate = prefs.getInt("maxBitRate", 0)
    )

    companion object {
        /** Sal aleatoria + token md5 para una contraseña. */
        fun makeAuth(password: String): Pair<String, String> {
            val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
            val salt = bytes.joinToString("") { "%02x".format(it) }
            return salt to md5(password + salt)
        }

        fun md5(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        fun normalizeUrl(raw: String): String {
            var u = raw.trim().trimEnd('/')
            if (u.isNotEmpty() && !u.startsWith("http://", true) && !u.startsWith("https://", true)) {
                u = "http://$u"
            }
            return u
        }
    }
}
