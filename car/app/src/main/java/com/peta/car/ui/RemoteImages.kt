package com.peta.car.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 원격 이미지(추천 장소 사진)를 받아 메모리에 둔다. 화면은 상태가 바뀔 때마다 다시 그려지므로
 * 한 번 받은 그림은 캐시에서 바로 꺼낸다. 실패하면 null이고 카드는 placeholder를 그대로 쓴다.
 */
object RemoteImages {
    private const val TIMEOUT_MILLIS = 4_000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // 가장 큰 사진도 카드 폭 정도로만 줄여서 들고 있는다. 총 12MB까지.
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = HashSet<String>()
    private val inFlight = HashMap<String, MutableList<(Bitmap?) -> Unit>>()

    fun cached(url: String): Bitmap? = cache.get(url)

    /** 캐시에 있으면 바로, 없으면 받아서 [onLoaded]로 알린다. 항상 메인 스레드에서 호출된다. */
    fun load(url: String, targetWidthPx: Int, onLoaded: (Bitmap?) -> Unit) {
        cache.get(url)?.let { onLoaded(it); return }
        if (url in failed) { onLoaded(null); return }
        inFlight[url]?.let { it.add(onLoaded); return }
        inFlight[url] = mutableListOf(onLoaded)
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { download(url, targetWidthPx) }
            if (bitmap != null) cache.put(url, bitmap) else failed.add(url)
            inFlight.remove(url)?.forEach { it(bitmap) }
        }
    }

    private fun download(url: String, targetWidthPx: Int): Bitmap? = try {
        val bytes = (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            try {
                if (responseCode != HttpURLConnection.HTTP_OK) return@run null
                inputStream.use { it.readBytes() }
            } finally {
                disconnect()
            }
        }
        bytes?.let { decode(it, targetWidthPx) }
    } catch (e: Exception) {
        null
    }

    private fun decode(bytes: ByteArray, targetWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
