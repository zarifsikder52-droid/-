package com.example.data.api

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class InMemoryCookieJar : CookieJar {
    private val cookieStore = HashMap<String, MutableList<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val stored = cookieStore[host] ?: ArrayList()
        stored.addAll(cookies)
        // Keep only unique cookies by name
        val unique = stored.associateBy { it.name }.values.toMutableList()
        cookieStore[host] = unique
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: return emptyList()
        val validCookies = mutableListOf<Cookie>()
        val iterator = cookies.iterator()
        while (iterator.hasNext()) {
            val cookie = iterator.next()
            if (cookie.expiresAt < System.currentTimeMillis()) {
                iterator.remove() // Remove expired cookies
            } else if (cookie.matches(url)) {
                validCookies.add(cookie)
            }
        }
        return validCookies
    }

    @Synchronized
    fun clear() {
        cookieStore.clear()
    }
}
