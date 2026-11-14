package com.example.tokcar

import com.example.tokcar.core.Countries
import com.example.tokcar.core.Dock
import com.example.tokcar.core.FakeVoiceInput
import com.example.tokcar.core.Gear
import com.example.tokcar.core.GearSource
import com.example.tokcar.core.LoginErr
import com.example.tokcar.core.LoginMode
import com.example.tokcar.core.LoginStep
import com.example.tokcar.core.Modal
import com.example.tokcar.core.NavState
import com.example.tokcar.core.Rules
import com.example.tokcar.core.Session
import com.example.tokcar.core.TokStore
import com.example.tokcar.core.UiState
import com.example.tokcar.core.Address
import com.example.tokcar.core.formatTyping
import com.example.tokcar.core.leftText
import com.example.tokcar.data.FakeServer
import com.example.tokcar.data.MyMsg
import com.example.tokcar.data.MyRouteMsg
import com.example.tokcar.data.Place
import com.example.tokcar.data.SeedLoader
import com.example.tokcar.data.ShareEnd
import com.example.tokcar.data.SysMsg
import com.example.tokcar.navi.Congestion
import com.example.tokcar.navi.NaviEvent
import com.example.tokcar.navi.NaviGateway
import com.example.tokcar.navi.TrafficSegment
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeGateway : NaviGateway {
    override val label = "가짜"
    override val maxWaypoints = 5
    val flow = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<NaviEvent> = flow
    val calls = mutableListOf<String>()
    override fun start() {}
    override fun release() {}
    override fun requestRoute(dest: Place, vias: List<Place>) { calls += "route:${dest.id}" }
    override fun addWaypoint(via: Place, position: Int) {}
    override fun removeWaypoint(position: Int) {}
    override fun cancelRoute() { calls += "cancel" }
    override fun openSearchScreen(keyword: String) { calls += "search:$keyword" }
}

private class FakeGear : GearSource {
    val state = MutableStateFlow(Gear.PARK)
    override val gear: Flow<Gear> = state
}

private class FakeSpeaker : com.example.tokcar.core.Speaker {
    val spoken = mutableListOf<String>()
    override fun speak(text: String) { spoken += text }
    override fun stop() {}
}

private class Fixture(scope: CoroutineScope, session: Session?) {
    val seed = SeedLoader.fromJson(File("src/main/assets/dummy/seed.json").readText())
    val server = FakeServer(seed, scope)
    val gateway = FakeGateway()
    val gear = FakeGear()
    var saved: Session? = session
    val speaker = FakeSpeaker()
    val store = TokStore(scope, server, { gateway }, voice = FakeVoiceInput("집에 가는 중", 100), speaker = speaker, gearSource = gear, session = session, onSessionChanged = { saved = it }, nowMs = { 1_700_000_000_000L })
    val ui: UiState get() = store.ui.value
}

@OptIn(ExperimentalCoroutinesApi::class)
class TokStoreTest {
    private val papa = Session("papa", "아빠", "01012345678", "+82 10-****-5678")

    private fun TestScope.fixture(session: Session? = papa) = Fixture(backgroundScope, session).also { runCurrent() }

    @Test
    fun newRoomIsNotSavedUntilFirstMessage() = runTest {
        val t = fixture()
        t.store.openNewRoom()
        t.store.toggleNewMember("kim"); t.store.toggleNewMember("nuna")
        t.store.createRoom()
        val id = t.ui.roomId!!
        assertTrue(t.ui.inChat)
        assertTrue("방을 만들어도 목록에는 아직 없음", t.ui.rooms.none { it.id == id })
        assertEquals("김 팀장, 누나", t.ui.room(id)?.name)

        t.store.back()
        assertNull("글을 안 보내고 나가면 버림", t.ui.room(id))

        t.store.openNewRoom(); t.store.toggleNewMember("park"); t.store.createRoom()
        val id2 = t.ui.roomId!!
        t.store.send(id2, "안녕"); runCurrent()
        assertEquals(id2, t.ui.orderedRooms.first().id)
        val msgs = t.ui.msgs.getValue(id2)
        assertTrue("만든 안내 글이 맨 앞", (msgs.first() as SysMsg).text.startsWith("아빠가 방을 만들었어요"))
        assertEquals("안녕", (msgs.last() as MyMsg).text)
        t.store.back()
        assertTrue(t.ui.rooms.any { it.id == id2 })
    }

    /** 시연용 스위치: 실제 내비를 쓰는 중이면 게이트웨이를 다시 만들고(저장된 값을 읽도록 먼저 저장), 같은 값이면 아무 일도 안 한다. */
    @Test
    fun demoMoveSwitchRebuildsTheRealGatewayAndIsSaved() = runTest {
        var saved: Boolean? = null
        val made = mutableListOf<FakeGateway>()
        val seed = SeedLoader.fromJson(File("src/main/assets/dummy/seed.json").readText())
        val store = TokStore(backgroundScope, FakeServer(seed, backgroundScope), { FakeGateway().also { made += it } }, initialReal = true, onDemoMoveChanged = { saved = it })
        runCurrent()
        assertEquals(1, made.size)
        assertFalse(store.ui.value.demoMove)
        store.setDemoMove(true); runCurrent()
        assertEquals(2, made.size)
        assertTrue(store.ui.value.demoMove)
        assertEquals(true, saved)
        store.setDemoMove(true); runCurrent()
        assertEquals("같은 값이면 다시 만들지 않음", 2, made.size)
        store.setDemoMove(false); runCurrent()
        assertEquals(3, made.size)
        assertEquals(false, saved)
    }

    @Test
    fun guideFromListOpensTheRoom() = runTest {
        val t = fixture()
        assertFalse(t.ui.inChat)
        t.store.directNav("family"); runCurrent()
        assertTrue("목록의 [안내]를 누르면 방으로 이동", t.ui.inChat)
        assertEquals("family", t.ui.roomId)
        assertEquals(NavState.PREVIEW, t.ui.nav.state)
        assertEquals("family", t.ui.pending?.roomId)
        // 안내 중에 다른 방의 [안내]: 확인창만 뜨고 이동은 확인한 뒤
        t.store.back()
        t.store.directNav("club"); runCurrent()
        assertTrue(t.ui.modal is Modal.Reroute)
        assertFalse(t.ui.inChat)
        t.store.rerouteOk(); runCurrent()
        assertTrue(t.ui.inChat)
        assertEquals("club", t.ui.roomId)
    }

    @Test
    fun trafficSegmentsShowOnlyWhileGuidingAndClearWhenTheRouteEnds() = runTest {
        val t = fixture()
        val segs = listOf(TrafficSegment(0.2f, 0.3f, Congestion.SLOW))
        // 안내 중이 아니면 받아도 정보 없음(null)
        t.gateway.flow.emit(NaviEvent.Traffic(segs)); runCurrent()
        assertNull(t.ui.traffic)
        // 안내 중에는 그대로 담김
        t.store.directNav("family"); runCurrent()
        t.gateway.flow.emit(NaviEvent.Guiding); runCurrent()
        t.gateway.flow.emit(NaviEvent.Traffic(segs)); runCurrent()
        assertEquals(segs, t.ui.traffic)
        // 새 정체 정보로 교체
        val next = listOf(TrafficSegment(0.5f, 0.6f, Congestion.HEAVY))
        t.gateway.flow.emit(NaviEvent.Traffic(next)); runCurrent()
        assertEquals(next, t.ui.traffic)
        // 「정체 없음」(빈 목록)과 「정보 없음」(null)은 다름: 막대가 전체 파랑이냐 회색이냐
        t.gateway.flow.emit(NaviEvent.Traffic(emptyList())); runCurrent()
        assertEquals(emptyList<TrafficSegment>(), t.ui.traffic)
        t.gateway.flow.emit(NaviEvent.Traffic(null)); runCurrent()
        assertNull(t.ui.traffic)
        // 안내를 끝내면 지움
        t.gateway.flow.emit(NaviEvent.Traffic(segs)); runCurrent()
        t.store.endGuide(); runCurrent()
        assertNull(t.ui.traffic)
    }

    @Test
    fun connectFailureFallsBackToDemoWithoutSaving() = runTest {
        var saved: Boolean? = null
        val seed = SeedLoader.fromJson(File("src/main/assets/dummy/seed.json").readText())
        val gws = mutableListOf<FakeGateway>()
        val store = TokStore(backgroundScope, FakeServer(seed, backgroundScope), { FakeGateway().also { gws += it } }, initialReal = true, onNaviModeChanged = { saved = it })
        runCurrent()
        assertTrue(store.ui.value.naviReal)
        gws[0].flow.emit(NaviEvent.ConnectFailed); runCurrent()
        assertFalse("연결 실패 시 데모로", store.ui.value.naviReal)
        assertEquals("내비에 연결하지 못해서 데모 GPS로 바꿨어요", store.ui.value.toast)
        assertNull("자동 전환은 저장하지 않음(다음에 다시 실제 내비 시도)", saved)
        store.setNaviReal(true)
        assertEquals("직접 바꾼 것은 저장", true, saved)
    }

    @Test
    fun cancelShareThenReenterAsksAgain() = runTest {
        val t = fixture()
        t.store.directNav("family"); runCurrent()
        assertTrue(t.ui.inChat)
        t.store.cancelShare()
        assertNull("방 안에서 취소한 직후에는 되묻지 않음", t.ui.dock(t.ui.room("family")))
        t.store.back()
        assertTrue("목록에서는 방 고르기 카드", t.ui.dock(null) is Dock.AskList)
        t.store.openRoom("family")
        assertTrue("다시 들어오면 이 방에 공유할까요?", t.ui.dock(t.ui.room("family")) is Dock.AskRoom)
        t.store.declineHere("family")
        t.store.back(); t.store.openRoom("family")
        assertNull("[닫기]는 다시 묻지 않음", t.ui.dock(t.ui.room("family")))
    }

    @Test
    fun cancelShareFromListAsksWhenEntering() = runTest {
        val t = fixture()
        t.store.directNav("family"); runCurrent()
        t.store.back()
        t.store.cancelShare()
        t.store.openRoom("family")
        assertTrue(t.ui.dock(t.ui.room("family")) is Dock.AskRoom)
    }

    @Test
    fun sharedRouteAndLocationAreAnnouncedByVoice() = runTest {
        val t = fixture()
        t.store.simulateIncoming("dest"); runCurrent()
        t.store.simulateIncoming("here"); runCurrent()
        assertEquals(listOf("민지님이 경로 공유를 시작했습니다", "엄마님이 위치 공유를 했습니다"), t.speaker.spoken)
    }

    @Test
    fun announcementIsHeldWhileListening() = runTest {
        val t = fixture()
        t.store.openRoom("family")
        t.store.mic("family"); runCurrent()
        assertTrue(t.ui.listening)
        t.store.simulateIncoming("here"); runCurrent()
        assertTrue("듣는 중에는 읽지 않고 모아 둠", t.speaker.spoken.isEmpty())
        advanceTimeBy(200); runCurrent()
        assertEquals(listOf("엄마님이 위치 공유를 했습니다"), t.speaker.spoken)
    }

    @Test
    fun pickingDestinationFromListOpensTheRoom() = runTest {
        val t = fixture()
        // 목록에서 목적지 팝업을 열어 다른 목적지를 고름 -> 방으로 들어감
        t.store.openDest("family")
        t.store.setDest("family", "stn"); runCurrent()
        assertTrue(t.ui.inChat)
        assertEquals("family", t.ui.roomId)
        assertEquals("stn", t.ui.nav.placeId)
        // 방 안에서 바꾸면 그대로 방에 머묾(다시 들어가는 동작 없음)
        t.store.setDest("family", "mart"); runCurrent()
        assertTrue(t.ui.inChat)
        // 목록의 최근 목적지 카드로 시작해도 방으로 들어감
        t.store.back(); t.store.endGuide()
        t.store.openDest("mom"); t.store.startDest(); runCurrent()
        assertTrue(t.ui.inChat)
        assertEquals("mom", t.ui.roomId)
    }

    @Test
    fun dmFromProfileIsDraftUntilSent() = runTest {
        val t = fixture()
        t.store.openDm("minji")
        assertTrue(t.ui.isDraft("dm-minji"))
        t.store.back()
        assertNull(t.ui.room("dm-minji"))
        t.store.openDm("minji"); t.store.send("dm-minji", "학원 끝났어?")
        assertFalse(t.ui.isDraft("dm-minji"))
        assertTrue(t.ui.rooms.any { it.id == "dm-minji" })
    }

    @Test
    fun draftRoomIsKeptWhenShareIsCounting() = runTest {
        val t = fixture()
        t.store.openDm("minji")
        t.store.setDest("dm-minji", "home"); runCurrent()
        assertEquals("dm-minji", t.ui.pending?.roomId)
        t.store.back()
        assertTrue("공유 카운트가 돌면 시작한 것으로 보고 저장", t.ui.rooms.any { it.id == "dm-minji" })
    }

    @Test
    fun inviteAddsMembersAndLeaveRemovesRoom() = runTest {
        val t = fixture()
        t.store.openRoom("family")
        t.store.openDrawer(); t.store.openInvite()
        t.store.toggleInvite("mother"); t.store.inviteDone()
        assertEquals(listOf("엄마", "민지", "어머니"), t.ui.room("family")?.members)
        assertEquals("아빠가 대화상대를 초대했어요 · 어머니", (t.ui.msgs.getValue("family").last() as SysMsg).text)
        assertEquals("1명을 초대했어요", t.ui.toast)

        t.store.directNav("family"); runCurrent()
        t.store.askLeave()
        assertEquals(Modal.Leave, t.ui.modal)
        t.store.leaveRoom()
        assertNull(t.ui.room("family"))
        assertNull("나간 방의 공유 카운트는 끝남", t.ui.pending)
        assertFalse(t.ui.inChat)
        t.server.simulateIncoming("here"); runCurrent()
        assertNull("나간 방으로 오는 위치는 무시", t.ui.room("family"))
    }

    @Test
    fun cancelShareHasNoToastAndMoveDockShowsTwoLines() = runTest {
        val t = fixture()
        t.store.directNav("club"); runCurrent()
        t.store.cancelShare()
        assertNull(t.ui.toast)
        t.store.directNav("club", force = true); runCurrent()
        advanceTimeBy(15_000); runCurrent()
        assertEquals("club", t.ui.sharing)
        val dock = t.ui.dock(t.ui.room("family")) as Dock.Move
        assertEquals("강남 모임 장소로 안내 중", dock.title)
        assertEquals("동창 모임", dock.fromName)
        t.store.stopShare()
        assertNull(t.ui.toast)
        assertEquals(ShareEnd.STOPPED, (t.ui.msgs.getValue("club").last() as MyRouteMsg).state)
    }

    /** 공유 중에 내비 앱에서 목적지를 바꾸면 앱에서 바꾼 것과 같다: 공유는 이어지고, 방 목적지·경로 공유 카드·[안내 종료] 자리가 새 목적지를 따른다. */
    @Test
    fun externalDestinationChangeWhileSharingActsLikeInAppChange() = runTest {
        val t = fixture()
        t.store.directNav("club"); runCurrent()
        advanceTimeBy(15_000); runCurrent()
        assertEquals("club", t.ui.sharing)
        val stn = t.ui.places.getValue("stn")
        t.gateway.flow.tryEmit(NaviEvent.ExternalRoute(stn.name, stn.addr, stn.lat, stn.lon)); runCurrent()
        assertEquals("club", t.ui.sharing)
        assertEquals("stn", t.ui.nav.placeId)
        val club = t.ui.room("club")!!
        assertEquals("방 목적지도 새 목적지", "stn", t.ui.destId(club))
        assertTrue("[안내 종료] 자리가 유지됨", t.ui.isEndSlot(club))
        val card = t.ui.msgs.getValue("club").filterIsInstance<MyRouteMsg>().last()
        assertEquals(stn.title, card.name)
        assertNull("공유 종료 표시가 붙지 않음", card.state)
        assertEquals("stn", t.ui.hist.getValue("club").first())
    }

    /**
     * 앱이 시킨 경로를 내비가 좌표를 몇 m 다르게, 이름을 주소 글자로 바꿔서 되돌려 알려도 목적지는 그대로다.
     * (실제 내비가 「집」을 「경기도 … (양지동)」으로 알려서 방 목적지가 별도 장소로 바뀌었다 돌아오기를 되풀이한 문제)
     */
    @Test
    fun navigationEchoingTheAppsOwnDestinationDoesNotChangeIt() = runTest {
        val t = fixture()
        t.store.directNav("club"); runCurrent()
        advanceTimeBy(15_000); runCurrent()
        val before = t.ui.nav.placeId!!
        val dest = t.ui.places.getValue(before)
        val placesBefore = t.ui.places.size
        t.gateway.flow.tryEmit(NaviEvent.ExternalRoute("경기도 ${dest.addr} (양지동)", "수정구 양지동", dest.lat + 0.00001, dest.lon + 0.00001)); runCurrent()
        assertEquals(before, t.ui.nav.placeId)
        assertEquals(before, t.ui.destId(t.ui.room("club")!!))
        assertEquals("별도 장소가 생기지 않음", placesBefore, t.ui.places.size)
        assertNull("목적지가 바뀌었다는 알림도 없음", t.ui.toast)
        assertEquals("club", t.ui.sharing)
    }

    /** 15초 카운트 중에 내비에서 목적지를 바꿔도 그 방의 목적지가 따라 바뀌고 카운트는 이어진다. */
    @Test
    fun externalDestinationChangeWhileCountingKeepsTheCountdown() = runTest {
        val t = fixture()
        t.store.directNav("club"); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        val stn = t.ui.places.getValue("stn")
        t.gateway.flow.tryEmit(NaviEvent.ExternalRoute(stn.name, stn.addr, stn.lat, stn.lon)); runCurrent()
        assertEquals("club", t.ui.pending?.roomId)
        assertEquals("stn", t.ui.destId(t.ui.room("club")!!))
        advanceTimeBy(10_000); runCurrent()
        assertEquals("club", t.ui.sharing)
        assertEquals(stn.title, t.ui.msgs.getValue("club").filterIsInstance<MyRouteMsg>().last().name)
    }

    /** 목록 아래 카드의 [방 고르기]로 방을 고르면 공유를 시작하고 그 방(채팅방)으로 들어간다. */
    @Test
    fun pickingARoomFromTheListOpensTheChat() = runTest {
        val t = fixture()
        t.store.openDest("family")
        t.store.setDest("family", "stn"); runCurrent()
        t.store.cancelShare()
        t.store.back()
        assertFalse(t.ui.inChat)
        assertTrue(t.ui.dock(null) is Dock.AskList)
        t.store.openPick()
        t.store.shareTripTo("club"); runCurrent()
        assertTrue("고른 방으로 이동", t.ui.inChat)
        assertEquals("club", t.ui.roomId)
        assertEquals("club", t.ui.pending?.roomId)
        assertNull(t.ui.modal)
    }

    @Test
    fun destMicSearchesInDemoAndOpensNaviWhenReal() = runTest {
        val t = fixture()
        t.store.openDest("family")
        t.store.destMic(); runCurrent()
        assertTrue(t.ui.listening)
        advanceTimeBy(200); runCurrent()
        val m = t.ui.modal as Modal.Dest
        assertTrue("데모: 가짜 검색 결과", m.results)
        assertEquals("집에 가는 중", m.query)
        t.store.setDest("family", "stn"); runCurrent()
        assertEquals(NavState.PREVIEW, t.ui.nav.state)
        assertEquals("stn", t.ui.nav.placeId)
    }

    @Test
    fun loginWithIdPasswordLocksAfterFiveFails() = runTest {
        val t = fixture(session = null)
        assertFalse(t.ui.signedIn)
        t.store.startLogin(LoginMode.LOGIN)
        t.store.setLoginId("papa")
        repeat(4) { t.store.setPw("wrong123"); t.store.doLogin() }
        assertEquals(LoginErr.BAD, t.ui.login.err)
        assertEquals(1, t.ui.login.left)
        t.store.setPw("wrong123"); t.store.doLogin()
        assertEquals("잠김", 0, t.ui.login.left)
        t.store.setPw("papa1234!"); t.store.doLogin()
        assertFalse("잠기면 맞는 비밀번호도 안 됨", t.ui.signedIn)

        // 비밀번호 찾기 -> 인증 -> 새 비밀번호 -> 잠금 풀림
        t.store.startLogin(LoginMode.RESETPW)
        assertEquals("papa", t.ui.login.rid)
        t.store.resetSend(); advanceTimeBy(400); runCurrent()
        assertEquals(LoginStep.CODE, t.ui.login.step)
        t.store.setLoginCode("123456"); t.store.verify(); advanceTimeBy(400); runCurrent()
        assertEquals(LoginStep.NEWPW, t.ui.login.step)
        t.store.setPw("newpass99"); t.store.setPw2("newpass99"); t.store.newPwNext()
        assertEquals(LoginStep.IDPW, t.ui.login.step)
        t.store.setPw("newpass99"); t.store.doLogin()
        assertTrue(t.ui.signedIn)
        assertEquals("papa", t.saved?.id)
    }

    @Test
    fun signupCreatesAccountWithIdCheck() = runTest {
        val t = fixture(session = null)
        t.store.startLogin(LoginMode.SIGNUP)
        t.store.setLoginPhone("01012345678")
        assertEquals("010-1234-5678", t.ui.login.phone)
        t.store.sendCode()
        assertEquals("이미 가입된 번호", LoginErr.KNOWN, t.ui.login.err)
        t.store.setLoginPhone("01099998888")
        t.store.sendCode(); advanceTimeBy(400); runCurrent()
        t.store.setLoginCode("000000"); t.store.verify(); advanceTimeBy(400); runCurrent()
        assertEquals(LoginStep.ACCOUNT, t.ui.login.step)
        t.store.setNewId("Papa"); t.store.checkId()
        assertEquals("소문자로 바뀌고 이미 있는 아이디", false, t.ui.login.idCheck?.ok)
        t.store.setNewId("minsu7"); t.store.checkId()
        assertEquals(true, t.ui.login.idCheck?.ok)
        t.store.setPw("minsu7pw1"); t.store.setPw2("minsu7pw1"); t.store.accountNext()
        assertEquals("아이디가 들어간 비밀번호는 안 됨", LoginStep.ACCOUNT, t.ui.login.step)
        t.store.setPw("hello2025"); t.store.setPw2("hello2025"); t.store.accountNext()
        assertEquals(LoginStep.PROFILE, t.ui.login.step)
        t.store.setLoginName("민수"); t.store.profileNext()
        t.store.agreeAll(); t.store.join()
        assertTrue(t.ui.signedIn)
        assertEquals("민수", t.ui.myName)
        assertEquals("+82 10-****-8888", t.ui.phoneMask)

        t.store.logout()
        t.store.startLogin(LoginMode.FINDID)
        t.store.setLoginPhone("010-9999-8888"); t.store.sendCode(); advanceTimeBy(400); runCurrent()
        t.store.setLoginCode("111111"); t.store.verify(); advanceTimeBy(400); runCurrent()
        assertEquals(listOf("minsu7"), t.ui.login.found)
        assertEquals("mi****", Rules.maskId("minsu7"))
    }

    @Test
    fun nameLessPlaceShowsAreaAsTitle() {
        assertEquals("진주시 가좌동", Address.area("경상남도 진주시 진주대로500번길 20 (가좌동)"))
        assertEquals("분당구 삼평동", Address.area("성남시 분당구 삼평동"))
        assertEquals("분당구 삼평동", Address.area("성남시 분당구 삼평동 123-4"))
        assertEquals("강남구 역삼동", Address.area("서울특별시 강남구 역삼동 테헤란로 152"))
        assertEquals("강남구 테헤란로", Address.area("서울특별시 강남구 테헤란로 152"))
        assertEquals("광주시 오포읍", Address.area("광주시 오포읍 능평리 12"))
        assertTrue(Address.looksLikeAddress("경상남도 진주시 진주대로500번길 20 (가좌동)"))
        assertTrue(Address.looksLikeAddress("서울특별시 강남구 테헤란로 152"))
        assertFalse(Address.looksLikeAddress("스타벅스 판교점"))
        assertFalse(Address.looksLikeAddress("집"))
        val named = Place("a", "스타벅스 판교점", "성남시 분당구 판교역로 166", 0.0, 0.0)
        val nameless = Place("b", "경상남도 진주시 진주대로500번길 20 (가좌동)", "경상남도 진주시 진주대로500번길 20 (가좌동)", 0.0, 0.0)
        assertEquals("스타벅스 판교점", named.title)
        assertEquals("진주시 가좌동", nameless.title)
        // 내비가 이름 칸에 전체 주소를, 주소 칸에는 짧게만 준 경우: 큰 글씨는 구 동, 작은 글씨는 이름 칸의 전체 주소
        val fromNavi = Place("c", "경상남도 진주시 진주대로500번길 20 (가좌동)", "진주시 가좌동", 0.0, 0.0)
        assertEquals("진주시 가좌동", fromNavi.title)
        assertEquals("전체 주소에서 도 이름은 뺌", "진주시 진주대로500번길 20 (가좌동)", fromNavi.fullAddr)
        assertEquals("강남구 테헤란로 152", Address.withoutProvince("서울특별시 강남구 테헤란로 152"))
        assertEquals("안성시 일죽면 판교길 58", Address.withoutProvince("경기도 안성시 일죽면 판교길 58"))
        assertEquals("성남시 분당구 판교로 242", Address.withoutProvince("성남시 분당구 판교로 242"))
        assertEquals("성남시 분당구 판교역로 166", named.fullAddr)
    }

    @Test
    fun remainingTimeIsDayHourMinute() {
        assertEquals("0분", leftText(0))
        assertEquals("18분", leftText(18))
        assertEquals("1시간", leftText(60))
        assertEquals("3시간 23분", leftText(203))
        assertEquals("1일", leftText(1440))
        assertEquals("1일 1시간", leftText(1500))
        assertEquals("1일 1시간 30분", leftText(1530))
    }

    @Test
    fun phoneTypingFormat() {
        assertEquals("010-1234-5678", formatTyping(Countries.of("KR"), "01012345678"))
        assertEquals("10-1234-5678", formatTyping(Countries.of("KR"), "1012345678"))
        assertEquals("(555) 123-4567", formatTyping(Countries.of("US"), "5551234567"))
        assertEquals("010-12", formatTyping(Countries.of("KR"), "01012"))
    }

    @Test
    fun drivingBlocksPlusMenuAndAddFriend() = runTest {
        val t = fixture()
        t.store.openRoom("family")
        t.gear.state.value = Gear.DRIVE; runCurrent()
        t.store.togglePlus()
        assertFalse(t.ui.plusOpen)
        t.store.openAddFriend(); t.store.setAddTel("01022223333"); t.store.findFriend()
        assertNull("주행 중에는 번호로 찾지 않음", (t.ui.modal as Modal.AddFriend).res)
        t.gear.state.value = Gear.PARK; runCurrent()
        t.store.findFriend()
        assertTrue((t.ui.modal as Modal.AddFriend).res != null)
    }
}
