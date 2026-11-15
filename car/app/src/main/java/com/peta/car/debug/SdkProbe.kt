package com.peta.car.debug

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * SDK 가용성 점검용 (시연 기능 아님). logcat 태그 SdkProbe 로 결과를 남긴다.
 * - FusedLocation 1.1.3: isSupported → initialize → getLastKnownLocation → 콜백 구독
 * - SpeechToText 3.3.1 (ON_DEVICE): initialize → request → 5초 뒤 stop
 */
object SdkProbe {
    private const val TAG = "SdkProbe"
    private val main = Handler(Looper.getMainLooper())
    private var started = false

    fun run(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        probeFusedLocation(app)
        probeStt(app)
    }

    private fun probeFusedLocation(app: Context) {
        runCatching {
            val fl = FusedLocation(app)
            val supported = fl.isSupported()
            Log.i(TAG, "FusedLocation.isSupported=$supported")
            if (!supported) return
            fl.initialize()
            Log.i(TAG, "FusedLocation.initialize ok")
            val listener = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    Log.i(TAG, "FusedLocation.onChanged $location")
                }
                override fun onError(exception: Exception) {
                    Log.w(TAG, "FusedLocation.onError ${exception.javaClass.simpleName}: ${exception.message}")
                }
            }
            fl.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    Log.i(TAG, "FusedLocation.lastKnown $location")
                }
                override fun onError(exception: Exception) {
                    Log.w(TAG, "FusedLocation.lastKnown error ${exception.javaClass.simpleName}: ${exception.message}")
                }
            })
            fl.registerFusedLocationCallback(listener)
            main.postDelayed({
                runCatching { fl.unregisterFusedLocationCallback(listener); fl.release() }
                Log.i(TAG, "FusedLocation released")
            }, 15_000)
        }.onFailure { Log.w(TAG, "FusedLocation probe threw", it) }
    }

    private fun probeStt(app: Context) {
        runCatching {
            val stt = SpeechToText(app, Mode.ON_DEVICE)
            stt.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    Log.i(TAG, "STT.initialize onSuccess (ON_DEVICE)")
                    runCatching { stt.setLocale(Locale.KOREAN) }.onFailure { Log.w(TAG, "STT.setLocale threw", it) }
                    val listener = object : ResultListener {
                        override fun onUpdated(text: String, isFinal: Boolean) { Log.i(TAG, "STT.onUpdated final=$isFinal text=$text") }
                        override fun onUpdatedEpdData(a: Long, b: Long) { Log.i(TAG, "STT.epd $a $b") }
                        override fun onStartedRecognition() { Log.i(TAG, "STT.onStartedRecognition") }
                        override fun onEndedRecognition() { Log.i(TAG, "STT.onEndedRecognition") }
                        override fun onError(errorType: ErrorType) { Log.w(TAG, "STT.onError $errorType") }
                        override fun onReady() { Log.i(TAG, "STT.onReady") }
                        override fun onUpdatedRms(rms: Double) = Unit
                    }
                    stt.addListener(listener)
                    runCatching { stt.request() }.onFailure { Log.w(TAG, "STT.request threw", it) }
                    main.postDelayed({
                        runCatching { stt.stop() }.onFailure { Log.w(TAG, "STT.stop threw", it) }
                        main.postDelayed({ runCatching { stt.removeListener(listener); stt.release() }; Log.i(TAG, "STT released") }, 2_000)
                    }, 6_000)
                }
                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "STT.initialize onFailure $errorType")
                }
            })
        }.onFailure { Log.w(TAG, "STT probe threw", it) }
    }
}
