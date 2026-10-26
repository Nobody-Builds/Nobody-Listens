package com.nobodybuilds.listens.data.ytmusic

import java.security.MessageDigest

object YouTubeMusicAuth {
    fun mergeCookies(vararg cookies: String): String {
        val merged = linkedMapOf<String, String>()
        cookies.forEach { cookie ->
            parseCookie(cookie).forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) merged[key] = value
            }
        }
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun parseCookie(cookie: String): Map<String, String> =
        cookie.split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { part ->
                val split = part.indexOf('=')
                if (split <= 0) null
                else part.substring(0, split).trim() to part.substring(split + 1).trim()
            }
            .toMap()

    fun hasLoginCookie(cookie: String): Boolean {
        if (cookie.isBlank()) return false
        val parsed = parseCookie(cookie)
        return parsed.containsKey("SAPISID") ||
            parsed.containsKey("__Secure-3PAPISID") ||
            parsed.containsKey("LOGIN_INFO")
    }

    fun sapisid(cookie: String): String? {
        val parsed = parseCookie(cookie)
        return parsed["SAPISID"] ?: parsed["__Secure-3PAPISID"]
    }

    fun sha1Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun authorizationHeader(cookie: String, origin: String, unixSeconds: Long): String? {
        val sapisid = sapisid(cookie) ?: return null
        val hash = sha1Hex("$unixSeconds $sapisid $origin")
        return "SAPISIDHASH ${unixSeconds}_$hash"
    }
}
