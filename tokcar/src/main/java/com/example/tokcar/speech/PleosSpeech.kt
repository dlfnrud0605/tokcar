package com.example.tokcar.speech

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.stt.listener.ResultListener as SttResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener as TtsEventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.tokcar.core.Speaker
import com.example.tokcar.core.VoiceException
import com.example.tokcar.core.VoiceInput
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/*
 * Pleos SpeechToText / TextToSpeech SDK 연결.
 * 공식문서(document.pleos.ai 의 SpeechToText·TextToSpeech SDK 가이드)의 순서를 따른다:
 * 객체 생성 -> initialize() -> 리스너 등록 -> setLocale() -> request()/speak() -> stop() -> 리스너 제거 후 release().
 * 두 SDK 모두 ON_DEVICE 모드를 쓴다: 차량의 온디바이스 엔진이라 네트워크가 필요 없고 앱 등록도 필요 없다(문서).
 * 필요한 권한은 AndroidManifest 의 pleos.car.permission.STT_SERVICE / TTS_SERVICE.
 */

/** 말하기: SpeechToText SDK. [listen] 한 번에 한 마디를 알아듣는다. */
class PleosVoiceInput(context: Context, private val log: (String) -> Unit = {}) : VoiceInput {
    private val app = context.applicationContext
    private val lock = Any()
    @Volatile private var stt: SpeechToText? = null
    @Volatile private var ready = false
    private var session: Session? = null

    /** 지금 듣고 있는 한 번의 말하기. [last] = 마지막으로 받은 인식 글(최종 결과가 안 와도 이것을 쓴다). */
    private class Session(val cont: CancellableContinuation<String?>) {
        var last: String = ""
    }

    private val listener = object : SttResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit

        override fun onUpdated(stt: String, completed: Boolean) {
            val s = synchronized(lock) { session } ?: return
            if (stt.isNotBlank()) s.last = stt.trim()
            // 최종 결과가 오면 거기서 끝낸다(SDK 가 알아서 멈추는지는 문서에 없어서 stop()도 불러 둠)
            if (completed && s.last.isNotEmpty()) finish(s, s.last)
        }

        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit

        override fun onEndedRecognition() {
            val s = synchronized(lock) { session } ?: return
            finish(s, s.last.ifEmpty { null })
        }

        override fun onError(errorType: SttErrorType) {
            val s = synchronized(lock) { session } ?: return
            log("[STT] 오류 = $errorType")
            fail(s, VoiceException(reasonOf(errorType)))
        }
    }

    private fun finish(s: Session, text: String?) {
        if (!detach(s)) return
        runCatching { stt?.stop() }
        if (s.cont.isActive) s.cont.resume(text)
    }

    private fun fail(s: Session, e: VoiceException) {
        if (!detach(s)) return
        runCatching { stt?.stop() }
        if (s.cont.isActive) s.cont.resumeWithException(e)
    }

    /** 이 말하기가 아직 진행 중이면 끝난 것으로 표시하고 true. 이미 끝났으면 false(결과·종료 이벤트가 겹쳐 와도 한 번만 처리). */
    private fun detach(s: Session): Boolean = synchronized(lock) {
        if (session !== s) false else { session = null; true }
    }

    override suspend fun listen(): String? {
        ensureReady()
        return suspendCancellableCoroutine { cont ->
            val s = Session(cont)
            synchronized(lock) { session = s }
            cont.invokeOnCancellation {
                if (detach(s)) runCatching { stt?.stop() }    // 다시 눌러 멈췄거나 시간이 지났을 때
            }
            try {
                stt?.request()
            } catch (e: Exception) {
                fail(s, VoiceException("음성 인식을 시작하지 못했어요"))
            }
        }
    }

    /** 처음 한 번만 초기화하고 리스너·언어를 정한다. */
    private suspend fun ensureReady() {
        if (ready) return
        suspendCancellableCoroutine { cont ->
            val s = stt ?: SpeechToText(app, SttMode.ON_DEVICE).also { stt = it }
            s.initialize(object : SttInitListener {
                override fun onSuccess() {
                    s.addListener(listener)
                    s.setLocale(Locale.KOREA)
                    ready = true
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onFailure(errorType: SttErrorType) {
                    log("[STT] 초기화 실패 = $errorType")
                    runCatching { s.release() }
                    stt = null    // 다음 시도에서 새로 만든다
                    if (cont.isActive) cont.resumeWithException(VoiceException(reasonOf(errorType)))
                }
            })
        }
    }

    override fun release() {
        val s = stt ?: return
        runCatching { s.removeListener(listener) }
        runCatching { s.release() }
        stt = null
        ready = false
    }

    private fun reasonOf(e: SttErrorType): String = when (e) {
        SttErrorType.PERMISSION_DENIED -> "음성 인식 권한이 없어요"
        SttErrorType.MIC_ERROR -> "마이크를 쓸 수 없어요"
        SttErrorType.UNAVAILABLE_LANGUAGE -> "이 언어는 음성 인식을 쓸 수 없어요"
        else -> "음성 인식에 실패했어요 (${e.name})"
    }
}

/**
 * 읽어 주기: TextToSpeech SDK. 문서에는 speak() 를 연달아 부르면 어떻게 되는지 설명이 없어서,
 * 앞 것이 끝나(onDone·onStop·onError)야 다음 것을 읽도록 이 클래스가 순서를 지킨다. 상태는 메인 스레드에서만 만진다.
 */
class PleosSpeaker(context: Context, private val log: (String) -> Unit = {}) : Speaker {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var initializing = false
    private var speaking = false
    private val queue = ArrayDeque<String>()

    /** 끝났다는 알림이 끝내 안 오면 큐가 멈춰 버리지 않게 풀어 준다. */
    private val watchdog = Runnable {
        log("[TTS] 재생 끝 알림이 오지 않아 다음으로 넘어가요")
        onFinished()
    }

    private val listener = object : TtsEventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { main.post { onFinished() } }
        override fun onStop() { main.post { onFinished() } }
        override fun onError(errorType: TtsErrorType) {
            log("[TTS] 오류 = $errorType")
            main.post { onFinished() }
        }
    }

    override fun speak(text: String) {
        main.post {
            queue.addLast(text)
            drain()
        }
    }

    override fun stop() {
        main.post {
            queue.clear()
            if (speaking) runCatching { tts?.stop() }    // onStop 이 오면 onFinished 로 정리됨
        }
    }

    private fun drain() {
        if (speaking || queue.isEmpty()) return
        if (!ready) { initialize(); return }
        val text = queue.removeFirst()
        speaking = true
        main.postDelayed(watchdog, WATCHDOG_MS)
        try {
            tts?.speak(text, true)
        } catch (e: Exception) {
            log("[TTS] speak 실패: ${e.message}")
            onFinished()
        }
    }

    private fun onFinished() {
        main.removeCallbacks(watchdog)
        speaking = false
        drain()
    }

    private fun initialize() {
        if (initializing) return
        initializing = true
        val t = tts ?: TextToSpeech(app, TtsMode.ON_DEVICE).also { tts = it }
        t.initialize(object : TtsInitListener {
            override fun onSuccess() {
                main.post {
                    t.addEventListener(listener)
                    t.setLocale(Locale.KOREA)
                    ready = true
                    initializing = false
                    drain()
                }
            }

            override fun onFailure(errorType: TtsErrorType) {
                log("[TTS] 초기화 실패 = $errorType")
                main.post {
                    initializing = false
                    queue.clear()
                    runCatching { t.release() }
                    tts = null    // 다음 메시지에서 다시 시도
                }
            }
        })
    }

    override fun release() {
        main.post {
            main.removeCallbacks(watchdog)
            queue.clear()
            val t = tts ?: return@post
            runCatching { t.removeEventListener(listener) }
            runCatching { t.release() }
            tts = null
            ready = false
            speaking = false
        }
    }

    private companion object {
        const val WATCHDOG_MS = 60_000L
    }
}
