package eu.kanade.tachiyomi.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import rx.Observable

@Deprecated("Use suspend APIs instead")
fun Call.asObservableSuccess(): Observable<Response> = Observable.fromCallable {
    clone().execute().also { response ->
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("HTTP ${response.code}")
        }
    }
}

suspend fun Call.awaitSuccess(): Response = withContext(Dispatchers.IO) {
    execute().also { response ->
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("HTTP ${response.code}")
        }
    }
}

fun OkHttpClient.newCachelessCallWithProgress(
    request: Request,
    listener: ProgressListener,
    existingSize: Long = 0L,
): Call = newBuilder()
    .cache(null)
    .addNetworkInterceptor { chain ->
        val ranged = chain.request().newBuilder().apply {
            if (existingSize > 0 && request.header("Range") == null) header("Range", "bytes=$existingSize-")
        }.build()
        val response = chain.proceed(ranged)
        response.newBuilder().body(
            ProgressBody(response.body, listener, if (response.code == 206) existingSize else 0),
        ).build()
    }
    .build()
    .newCall(request)

private class ProgressBody(
    private val responseBody: ResponseBody,
    private val listener: ProgressListener,
    private val existingSize: Long,
) : ResponseBody() {
    private val source: BufferedSource by lazy {
        object : ForwardingSource(responseBody.source()) {
            var bytesRead = existingSize
            override fun read(sink: Buffer, byteCount: Long): Long {
                val count = super.read(sink, byteCount)
                if (count > 0) bytesRead += count
                val total = responseBody.contentLength().let { if (it >= 0) it + existingSize else -1L }
                listener.update(bytesRead, total, count == -1L)
                return count
            }
        }.buffer()
    }

    override fun contentType(): MediaType? = responseBody.contentType()
    override fun contentLength(): Long = responseBody.contentLength()
    override fun source(): BufferedSource = source
}
