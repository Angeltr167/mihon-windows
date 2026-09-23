@file:Suppress("FunctionName")

package eu.kanade.tachiyomi.network

import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import kotlin.time.Duration.Companion.minutes

private val defaultCache = CacheControl.Builder().maxAge(10.minutes).build()

fun GET(url: String, headers: Headers = Headers.Builder().build(), cache: CacheControl = defaultCache): Request =
    GET(url.toHttpUrl(), headers, cache)

fun GET(url: HttpUrl, headers: Headers = Headers.Builder().build(), cache: CacheControl = defaultCache): Request =
    Request.Builder().url(url).headers(headers).cacheControl(cache).build()

fun POST(
    url: String,
    headers: Headers = Headers.Builder().build(),
    body: RequestBody = FormBody.Builder().build(),
    cache: CacheControl = defaultCache,
): Request = Request.Builder().url(url).post(body).headers(headers).cacheControl(cache).build()

fun PUT(
    url: String,
    headers: Headers = Headers.Builder().build(),
    body: RequestBody = FormBody.Builder().build(),
    cache: CacheControl = defaultCache,
): Request = Request.Builder().url(url).put(body).headers(headers).cacheControl(cache).build()

fun DELETE(
    url: String,
    headers: Headers = Headers.Builder().build(),
    body: RequestBody = FormBody.Builder().build(),
    cache: CacheControl = defaultCache,
): Request = Request.Builder().url(url).delete(body).headers(headers).cacheControl(cache).build()
