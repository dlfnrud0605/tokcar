package com.example.tokcar.core

/** 말하기(음성 인식)가 실패한 이유. [reason] 은 토스트로 그대로 보인다. */
class VoiceException(val reason: String) : Exception(reason)

/**
 * 소리 내어 읽어 주는 곳(음성 합성). 실제는 Pleos TextToSpeech SDK([com.example.tokcar.speech.PleosSpeaker]).
 * 여러 개를 연달아 부르면 앞 것이 끝난 뒤 차례로 읽는다.
 */
interface Speaker {
    fun speak(text: String)

    /** 읽는 중인 것을 멈추고 기다리는 것도 비운다. */
    fun stop()

    fun release() = Unit
}

/** 소리를 내지 않는 환경(테스트 등). */
object NoSpeaker : Speaker {
    override fun speak(text: String) = Unit
    override fun stop() = Unit
}

/**
 * 말하기(음성 인식). null 이면 알아듣지 못한 것, 인식을 못 하는 사정이 있으면 [VoiceException].
 * 코루틴이 취소되면 듣기를 멈춘다. 실제는 Pleos SpeechToText SDK([com.example.tokcar.speech.PleosVoiceInput]).
 */
interface VoiceInput {
    suspend fun listen(): String?

    fun release() = Unit
}

/** 테스트·더미: 잠깐 듣는 척하고 정해 둔 문장을 돌려준다. */
class FakeVoiceInput(private val text: String = "곧 도착해요", private val listenMs: Long = 1400) : VoiceInput {
    override suspend fun listen(): String? {
        kotlinx.coroutines.delay(listenMs)
        return text
    }
}
