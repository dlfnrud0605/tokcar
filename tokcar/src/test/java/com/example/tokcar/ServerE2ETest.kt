package com.example.tokcar

import com.example.tokcar.navi.Congestion
import com.example.tokcar.navi.TrafficSegment
import com.example.tokcar.remote.OkHttpRemote
import com.example.tokcar.remote.RemoteEvent
import com.example.tokcar.remote.RemoteLogin
import com.example.tokcar.remote.SLive
import com.example.tokcar.remote.SPlace
import com.example.tokcar.remote.Wire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 실제 가족 서버(server)에 붙어 보는 연결 확인. 서버를 켜고 주소를 넣었을 때만 돈다:
 *   TOKCAR_E2E=http://localhost:8080 ./gradlew :tokcar:testDebugUnitTest --tests '*ServerE2ETest*'
 * 가계정 papa · minji 의 비밀번호는 서버의 application-local.properties 와 같아야 한다.
 */
class ServerE2ETest {
    private val base = System.getenv("TOKCAR_E2E").orEmpty()

    @Test
    fun papaAndMinjiTalkAndShareARouteThroughTheRealServer() = runBlocking {
        assumeTrue("TOKCAR_E2E 가 없으면 건너뜀", base.isNotBlank())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val papa = OkHttpRemote(base, scope)
        val minji = OkHttpRemote(base, scope)
        try {
            papa.token = (papa.login("papa", "papa1234!") as RemoteLogin.Ok).token
            val minjiLogin = minji.login("minji", "minji1234!") as RemoteLogin.Ok
            minji.token = minjiLogin.token
            assertEquals("민지", minjiLogin.me.name)

            assertTrue(papa.friends().map { it.name }.containsAll(listOf("민지", "엄마", "김 팀장")))
            val room = papa.rooms().single { it.name == "우리 가족" }
            assertEquals(setOf("아빠", "민지", "엄마"), room.members.map { it.name }.toSet())
            assertTrue(papa.rooms().map { it.name }.containsAll(listOf("어머니집", "동창 모임", "출근 카풀")))
            papa.addPlace(room.id, SPlace("서현역", "성남시 분당구 서현로 170", 37.385, 127.123))
            assertEquals("서현역", papa.places(room.id).first().name)

            // 둘 다 붙인다
            val papaUp = scope.listen { papa.events.first { it == RemoteEvent.Connected } }
            val minjiUp = scope.listen { minji.events.first { it == RemoteEvent.Connected } }
            papa.connect(); minji.connect()
            withTimeout(5_000) { papaUp.await(); minjiUp.await() }

            // 글: 아빠 ack, 민지 message.new
            val text = "E2E ${System.currentTimeMillis()}"
            val gotNew = scope.listen { minji.events.first { it is RemoteEvent.New && it.message.text == text } as RemoteEvent.New }
            val gotAck = scope.listen { papa.events.first { it is RemoteEvent.Ack && it.ref == "e-1" } as RemoteEvent.Ack }
            assertTrue(papa.send(Wire.frame("text.send", room.id, "e-1").put("text", text)))
            val ack = withTimeout(5_000) { gotAck.await() }
            assertEquals("TEXT", ack.message.kind)
            assertEquals(ack.message.id, withTimeout(5_000) { gotNew.await() }.message.id)

            // 읽음: 민지가 읽으면 아빠가 받음
            val gotRead = scope.listen { papa.events.first { it is RemoteEvent.Read && it.msgId == ack.message.id } as RemoteEvent.Read }
            minji.send(Wire.frame("read", room.id).put("msgId", ack.message.id))
            assertEquals(minjiLogin.me.id, withTimeout(5_000) { gotRead.await() }.userId)

            // 경로 공유: 시작 → 실시간(정체 포함) → 도착
            val now = System.currentTimeMillis()
            val startAck = scope.listen { papa.events.first { it is RemoteEvent.Ack && it.ref == "e-2" } as RemoteEvent.Ack }
            papa.send(Wire.frame("route.end", room.id).put("reason", "STOPPED"))    // 지난 시험에서 남은 공유가 있으면 정리
            papa.send(
                Wire.frame("route.start", room.id, "e-2")
                    .put("from", Wire.placeJson(SPlace("", "성남시 분당구 판교로 242", 37.40, 127.10)))
                    .put("dest", Wire.placeJson(SPlace("민지 학원", "성남시 분당구 정자일로 95", 37.3671, 127.1086)))
                    .put("live", Wire.liveJson(SLive(37.40, 127.10, 900, 0, now + 900_000, null))),
            )
            val card = withTimeout(5_000) { startAck.await() }.message
            assertEquals("ACTIVE", card.route!!.state)
            assertEquals(900, card.route!!.live!!.remainSec)

            val gotLive = scope.listen { minji.events.first { it is RemoteEvent.Live && it.messageId == card.id } as RemoteEvent.Live }
            val traffic = listOf(TrafficSegment(0f, 0.4f, Congestion.FAST), TrafficSegment(0.4f, 1f, Congestion.HEAVY))
            papa.send(Wire.liveJson(SLive(37.39, 127.105, 600, 33, now + 600_000, traffic)).put("type", "route.live").put("roomId", room.id))
            val live = withTimeout(5_000) { gotLive.await() }.live
            assertEquals(33, live.pct)
            assertEquals(Congestion.HEAVY, live.traffic!![1].level)
            assertEquals((now + 600_000) / 1000, live.etaAtMs / 1000)

            val gotEnd = scope.listen { minji.events.first { it is RemoteEvent.Updated && it.message.id == card.id } as RemoteEvent.Updated }
            papa.send(Wire.frame("route.end", room.id).put("reason", "ARRIVED"))
            assertEquals("ARRIVED", withTimeout(5_000) { gotEnd.await() }.message.route!!.state)

            // REST 로 다시 받아도 같은 카드
            val again = minji.messages(room.id, after = card.id - 1).first { it.id == card.id }
            assertEquals("민지 학원", again.route!!.dest.name)

            // 방 만들기 · 초대 · 나가기
            val friends = papa.friends()
            val made = papa.createRoom("", listOf(friends.first { it.name == "엄마" }.id))
            assertEquals("엄마", made.name)
            val invited = papa.invite(made.id, listOf(friends.first { it.name == "누나" }.id))
            assertEquals(setOf("아빠", "엄마", "누나"), invited.members.map { it.name }.toSet())
            val rejected = runCatching { papa.invite(made.id, listOf(friends.first { it.name == "누나" }.id)) }.exceptionOrNull()
            assertEquals("ALREADY_MEMBER", (rejected as com.example.tokcar.remote.RemoteRejected).code)
            papa.leave(made.id)
            assertTrue(papa.rooms().none { it.id == made.id })
        } finally {
            papa.disconnect(); minji.disconnect()
            scope.cancel()
        }
    }

    /** 보내기 전에 먼저 듣기 시작해야 놓치지 않는다(바로 구독). */
    private fun <T> CoroutineScope.listen(block: suspend () -> T): Deferred<T> = async(start = CoroutineStart.UNDISPATCHED, block = { block() })
}
