package com.peta.car.debug

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 플로우 점검 페이지(모바일 dev 서버의 /admin)에서 보낸 화면 이동 명령을 받는다. 시연·개발 전용.
 * 서비스가 1초마다 [sync]로 지금 상태를 보내고 새 명령을 받아 온다.
 * 상태 쪽은 서비스가, 화면 안 값(탭·응급·팝업)은 MainActivity가 [applied]를 보고 맞춘다.
 */
object DebugBridge {
    data class Command(val id: Long, val action: String)

    /** MainActivity가 지금 그리는 화면 이름 (Screen.name) */
    @Volatile
    var screen: String = ""

    private val _applied = MutableStateFlow<Command?>(null)
    /** 서비스가 상태를 맞춘 마지막 명령. 화면은 이 값을 보고 자기 몫을 맞춘다. */
    val applied: StateFlow<Command?> = _applied.asStateFlow()

    private var lastId = 0L

    fun markApplied(command: Command) {
        _applied.value = command
    }

    /** 상태를 보내고, 아직 처리하지 않은 새 명령이 있으면 돌려준다. 서버가 꺼져 있으면 null. */
    suspend fun sync(url: String, status: JSONObject): Command? = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(status.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val cmd = body.optJSONObject("command") ?: return@withContext null
            val id = cmd.optLong("id")
            if (id <= lastId) return@withContext null
            val first = lastId == 0L
            lastId = id
            // 앱을 새로 켰을 때 예전 명령을 다시 실행하지 않는다
            if (first && cmd.optLong("ageMs") > FRESH_MILLIS) return@withContext null
            Command(id, cmd.optString("action"))
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private const val TIMEOUT_MILLIS = 1_500
    private const val FRESH_MILLIS = 5_000L
}
