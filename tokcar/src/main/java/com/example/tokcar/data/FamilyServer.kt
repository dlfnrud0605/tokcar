package com.example.tokcar.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** 서버가 앱에 먼저 알려 주는 일. */
sealed interface ServerEvent {
    /** 상대가 내 글을 읽었다. */
    data class Read(val roomId: String, val msgId: Long, val who: String) : ServerEvent

    /** 가족이 위치를 보냈다. */
    data class LocationReceived(val spec: IncomingSpec) : ServerEvent

    /** 가족이 글을 보냈다. */
    data class MessageReceived(val roomId: String, val who: String, val text: String) : ServerEvent
}

/** 서버가 받은 내 위치(공유 중일 때 올려 보낸 값). 더미 서버가 기록해 둔다. */
data class SharedLocation(val roomId: String, val lat: Double, val lon: Double, val remainSec: Int)

/**
 * 가족 서버와 주고받는 부분. 진짜 서버가 생기면 이 인터페이스를 구현해 끼우면 된다.
 * 지금은 [FakeServer]가 assets의 더미 데이터로 흉내 낸다.
 */
interface FamilyServer {
    val seed: Seed
    val events: SharedFlow<ServerEvent>

    /** 번호([e164] 예: +821012345678)가 가입돼 있으면 가입한 이름, 아니면 null. */
    fun registeredName(e164: String): String?

    /** 가입을 마친다. */
    fun register(e164: String, name: String)

    /** 인증번호 문자를 보낸다. [phone] 은 E.164 번호. */
    suspend fun sendCode(phone: String): Boolean

    /** 인증번호 확인. 더미 서버는 숫자 6자리면 통과시킨다. */
    suspend fun verify(phone: String, code: String): Boolean

    /** 번호로 가입자를 찾는다. */
    fun lookup(tel: String, myPhone: String, friends: List<Friend>): PhoneLookup

    /** 내가 방에 글을 보냈다. 상대가 읽는 일을 서버가 알려 준다. */
    fun messageSent(roomId: String, msgId: Long, members: List<String>)

    /** 위치 공유가 시작됐다/멈췄다. */
    fun shareStarted(roomId: String)
    fun shareStopped(roomId: String)

    /** 공유 중인 내 위치를 올려 보낸다. */
    fun pushLocation(location: SharedLocation)

    /** (개발용) 가족 폰에서 위치·글을 보낸 것처럼 만든다. key: "here"·"dest"(위치) 또는 "text"(글). */
    fun simulateIncoming(key: String)

    // ───── 아이디·비밀번호 계정(TokCar) ─────
    // 실제 서버는 비밀번호를 해시로만 저장해야 함(개인정보 보호법 시행령 제30조). 더미 서버는 화면용이라 메모리에 그대로 둔다.

    /** 이미 쓰는 아이디(또는 쓸 수 없는 예약어)인가. */
    fun idTaken(id: String): Boolean

    fun account(id: String): Account?

    /** 아이디·비밀번호 확인. 연속으로 [LOCK_N]번 틀리면 잠긴다(비밀번호를 새로 만들면 풀림). */
    fun login(id: String, pw: String): LoginResult

    /** 남은 시도 횟수(잠겼으면 0). */
    fun triesLeft(id: String): Int

    /** 이 번호(E.164)로 만든 아이디들. */
    fun findIds(e164: String): List<String>

    fun createAccount(account: Account)

    fun resetPassword(id: String, pw: String)

    companion object {
        const val LOCK_N = 5
    }
}

/** 가입한 계정. [phone] 은 입력한 그대로의 번호(나라 안 표기), [key] 는 E.164. */
data class Account(val id: String, val pw: String, val key: String, val country: String, val phone: String, val name: String)

sealed interface LoginResult {
    data class Ok(val account: Account) : LoginResult
    data class Bad(val left: Int) : LoginResult
    data object Locked : LoginResult
}

/** 서버 대신 assets/dummy/seed.json 의 값으로 응답하는 가짜 서버. 값은 메모리에만 있다. */
class FakeServer(
    override val seed: Seed,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
) : FamilyServer {

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    /** 서버가 받아 둔 위치 공유 기록(마지막 값). 점검용. */
    val lastShared = mutableMapOf<String, SharedLocation>()
    private var pushCount = 0

    /** 가입된 번호(E.164) -> 이름. 시안에서는 내 번호만 가입돼 있다. */
    private val registered = mutableMapOf<String, String>().also { m ->
        if (seed.myPhone.startsWith("0")) m["+82" + seed.myPhone.drop(1)] = seed.myName
    }

    override fun registeredName(e164: String): String? = registered[e164]

    override fun register(e164: String, name: String) {
        registered[e164] = name
    }

    override suspend fun sendCode(phone: String): Boolean {
        delay(SERVER_DELAY_MS)
        log("[서버] 인증번호 발송(더미): $phone")
        return true
    }

    override suspend fun verify(phone: String, code: String): Boolean {
        delay(SERVER_DELAY_MS)
        return Regex("^\\d{6}$").matches(code)
    }

    override fun lookup(tel: String, myPhone: String, friends: List<Friend>): PhoneLookup {
        val friend = friends.firstOrNull { it.tel == tel }
        val record = seed.phonebook.firstOrNull { it.tel == tel }
        return when {
            tel == myPhone -> PhoneLookup.Self
            friend != null -> PhoneLookup.Already(friend)
            record != null -> PhoneLookup.Found(record, tel)
            else -> PhoneLookup.NotRegistered(tel)
        }
    }

    override fun messageSent(roomId: String, msgId: Long, members: List<String>) {
        members.forEach { name ->
            val ms = if (seed.readDelaysMs.containsKey(name)) seed.readDelaysMs[name] else DEFAULT_READ_MS
            if (ms == null) return@forEach    // 계속 안 읽는 사람
            scope.launch {
                delay(ms)
                _events.emit(ServerEvent.Read(roomId, msgId, name))
            }
        }
    }

    override fun shareStarted(roomId: String) = log("[서버] 위치 공유 시작: $roomId")

    override fun shareStopped(roomId: String) {
        lastShared.remove(roomId)
        log("[서버] 위치 공유 멈춤: $roomId")
    }

    override fun pushLocation(location: SharedLocation) {
        lastShared[location.roomId] = location
        if (++pushCount % 10 == 1) log("[서버] 내 위치 수신 ${location.lat}, ${location.lon} (남은 ${location.remainSec}초)")
    }

    override fun simulateIncoming(key: String) {
        seed.incoming[key]?.let { spec -> scope.launch { _events.emit(ServerEvent.LocationReceived(spec)) } }
            ?: seed.incomingText[key]?.let { m -> scope.launch { _events.emit(ServerEvent.MessageReceived(m.roomId, m.who, m.text)) } }
    }

    private val accounts = mutableMapOf(
        "papa" to Account("papa", "papa1234!", "+821012345678", "KR", "01012345678", seed.myName),   // 시안 가입자: 010-1234-5678
    )
    private val fails = mutableMapOf<String, Int>()

    override fun idTaken(id: String): Boolean = id in accounts || id in RESERVED_IDS

    override fun account(id: String): Account? = accounts[id]

    override fun login(id: String, pw: String): LoginResult {
        if (triesLeft(id) <= 0) return LoginResult.Locked
        val a = accounts[id]
        if (a == null || a.pw != pw) {
            fails[id] = (fails[id] ?: 0) + 1
            return if (triesLeft(id) <= 0) LoginResult.Locked else LoginResult.Bad(triesLeft(id))
        }
        fails[id] = 0
        return LoginResult.Ok(a)
    }

    override fun triesLeft(id: String): Int = (FamilyServer.LOCK_N - (fails[id] ?: 0)).coerceAtLeast(0)

    override fun findIds(e164: String): List<String> = accounts.values.filter { it.key == e164 }.map { it.id }

    override fun createAccount(account: Account) {
        accounts[account.id] = account
        registered[account.key] = account.name
    }

    override fun resetPassword(id: String, pw: String) {
        accounts[id]?.let { accounts[id] = it.copy(pw = pw) }
        fails[id] = 0
    }

    companion object {
        private val RESERVED_IDS = setOf("admin", "test", "mama", "minji", "warak", "root")
        private const val SERVER_DELAY_MS = 300L
        private const val DEFAULT_READ_MS = 3000L
    }
}
