package com.peta.car.voice

import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale
import ai.pleos.playground.tts.TextToSpeech as PleosTts

/**
 * 주행 중 음성 안내 (C05: 한 문장 질문 + "응"/"아니" 두 단어 응답).
 * - 말하기: Pleos TextToSpeech SDK (온디바이스)
 * - 듣기: Android SpeechRecognizer → 차량 음성 인식 서비스(Gleo, ai.umos.ambientai)
 */
enum class StopChange { CHANGE, SKIP }

class VoiceAssistant(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var pending: Pair<String, (() -> Unit)?>? = null
    private var currentDone: (() -> Unit)? = null
    private var ready = false
    private var failed = false
    private var recognizer: SpeechRecognizer? = null

    /** 설정(C14) 「음성 안내」. 꺼지면 말하지 않고 바로 다음 단계로 넘어간다. */
    @Volatile
    var enabled: Boolean = true

    private val tts = PleosTts(appContext, Mode.ON_DEVICE)

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = finishSpeaking()
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "TTS error $errorType")
            finishSpeaking()
        }

        override fun onStop() = finishSpeaking()
        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        // SDK 규칙: initialize() 이후에 리스너를 붙인다
        runCatching {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    Log.i(TAG, "Pleos TTS ready")
                    runCatching { tts.addEventListener(events) }.onFailure { Log.w(TAG, "addEventListener failed", it) }
                    runCatching { tts.setLocale(Locale.KOREAN) }
                    main.post {
                        ready = true
                        pending?.let { (text, done) -> speak(text, done) }
                        pending = null
                    }
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "Pleos TTS init failed: $errorType")
                    main.post { giveUp() }
                }
            })
        }.onFailure {
            Log.w(TAG, "Pleos TTS unavailable", it)
            giveUp()
        }
    }

    private fun giveUp() {
        failed = true
        pending?.second?.invoke()
        pending = null
    }

    private fun finishSpeaking() {
        main.post {
            val done = currentDone
            currentDone = null
            done?.invoke()
        }
    }

    /** 말하고 끝나면 onDone. 음성이 안 되는 환경에서도 흐름이 멈추지 않게 바로 onDone을 부른다. */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        Log.i(TAG, "speak: $text")
        when {
            failed || !enabled -> onDone?.let { main.post(it) }
            !ready -> pending = text to onDone
            else -> {
                currentDone = onDone
                runCatching { tts.speak(text, true) }.onFailure {
                    Log.w(TAG, "speak failed", it)
                    finishSpeaking()
                }
            }
        }
    }

    /** "응"이면 true, "아니"면 false, 못 알아들었거나 시간이 지나면 null */
    fun listenYesNo(timeoutMillis: Long, onResult: (Boolean?) -> Unit) =
        listen(timeoutMillis) { texts -> onResult(texts?.let(::parseYesNo)) }

    /** C05-V: "바꿔 줘" = CHANGE, "쉬지 않고 가" = SKIP, 그 밖 = null */
    fun listenStopChange(timeoutMillis: Long, onResult: (StopChange?) -> Unit) =
        listen(timeoutMillis) { texts -> onResult(texts?.let(::parseStopChange)) }

    /** C12 검색어 (자유 발화). 못 알아들으면 null */
    fun listenText(timeoutMillis: Long, onResult: (String?) -> Unit) =
        listen(timeoutMillis) { texts -> onResult(texts?.firstOrNull()?.takeIf { it.isNotBlank() }) }

    /** 인식 결과 후보 목록. 실패·시간 초과면 null */
    private fun listen(timeoutMillis: Long, onResult: (List<String>?) -> Unit) {
        var delivered = false
        fun deliver(texts: List<String>?) {
            if (delivered) return
            delivered = true
            recognizer?.destroy()
            recognizer = null
            Log.i(TAG, "heard: $texts")
            onResult(texts)
        }
        main.postDelayed({ deliver(null) }, timeoutMillis)

        val canListen = SpeechRecognizer.isRecognitionAvailable(appContext) &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!canListen) {
            Log.w(TAG, "음성 인식 불가 (서비스 없음 또는 마이크 권한 없음)")
            return
        }
        recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()?.apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    deliver(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "recognizer error $error")
                    deliver(null)
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            )
        }
    }

    fun release() {
        recognizer?.destroy()
        runCatching { tts.removeEventListener(events) }
        runCatching { tts.release() }
    }

    companion object {
        private const val TAG = "PetaVoice"
        private val YES = Regex("응|네|예|그래|좋아|들러|들르|가자|오케이")
        private val NO = Regex("아니|싫어|괜찮아|그냥 가|안 들")
        private val CHANGE = Regex("바꿔|바꾸|다른 곳|다른 데")
        private val SKIP = Regex("쉬지 않|안 쉬|그냥 가|취소|바로 가")

        fun parseStopChange(texts: List<String>): StopChange? {
            for (t in texts) {
                if (SKIP.containsMatchIn(t)) return StopChange.SKIP
                if (CHANGE.containsMatchIn(t)) return StopChange.CHANGE
            }
            return null
        }

        fun parseYesNo(texts: List<String>): Boolean? {
            for (t in texts) {
                if (NO.containsMatchIn(t)) return false
                if (YES.containsMatchIn(t)) return true
            }
            return null
        }
    }
}
