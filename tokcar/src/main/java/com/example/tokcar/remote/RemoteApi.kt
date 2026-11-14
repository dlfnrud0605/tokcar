package com.example.tokcar.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 로그인 결과. */
sealed interface RemoteLogin {
    data class Ok(val token: String, val me: SUser) : RemoteLogin
    data class Bad(val left: Int?) : RemoteLogin
    data object Locked : RemoteLogin
    /** 서버에 못 붙음 등. */
    data class Failed(val reason: String) : RemoteLogin
}

/** 서버가 WebSocket 으로 먼저 알려 주는 일(API 명세 4절 「서버 → 앱」). */
sealed interface RemoteEvent {
    data object Connected : RemoteEvent
    data object Disconnected : RemoteEvent
    data class Ack(val ref: String?, val message: SMessage) : RemoteEvent
    data class New(val message: SMessage) : RemoteEvent
    data class Updated(val message: SMessage) : RemoteEvent
    data class Live(val roomId: Long, val messageId: Long, val live: SLive) : RemoteEvent
    data class Read(val roomId: Long, val userId: Long, val msgId: Long) : RemoteEvent
    data class Error(val ref: String?, val code: String, val message: String) : RemoteEvent
    /** 방이 생겼거나 멤버 · 이름이 바뀜(방 만들기 · 초대 · 누가 나감). 모르는 방이면 목록에 넣는다. */
    data class RoomUpdated(val room: SRoom) : RemoteEvent
    /** 그 방에서 빠짐(내가 나감, 다른 기기에서 나간 것 포함). */
    data class RoomRemoved(val roomId: Long) : RemoteEvent
    /** (개발용) 서버가 그 방을 처음 상태로 돌렸다(대화 · 경로 공유 삭제). */
    data class RoomCleared(val roomId: Long) : RemoteEvent
}

/** 서버가 거절한 요청(친구가 아님 · 이미 방에 있음 등). [message] 는 그대로 보여 줄 안내 글. */
class RemoteRejected(val code: String, message: String) : IOException(message)

/** 토큰이 없거나 서버가 모르는 토큰(서버를 다시 켰을 때 등). 다시 로그인해야 함. */
class RemoteUnauthorized : IOException("다시 로그인해 주세요")

/** 가족 서버. REST 조회와 WebSocket 실시간. 테스트에서는 가짜로 바꿔 끼운다. */
interface RemoteApi {
    val events: SharedFlow<RemoteEvent>
    var token: String?

    suspend fun login(loginId: String, password: String): RemoteLogin
    suspend fun logout()
    suspend fun me(): SUser
    suspend fun friends(): List<SFriend>
    suspend fun rooms(): List<SRoom>
    /** [after] 가 있으면 그 뒤 것 전부, 없으면 최신 50개. 오래된 것 → 새것 순. */
    suspend fun messages(roomId: Long, after: Long? = null): List<SMessage>
    suspend fun places(roomId: Long): List<SPlace>
    suspend fun addPlace(roomId: Long, place: SPlace): List<SPlace>
    /** 방 만들기: 나 + [memberIds]. 이름이 비면 서버가 친구 이름으로 짓는다. */
    suspend fun createRoom(name: String, memberIds: List<Long>): SRoom
    suspend fun invite(roomId: Long, userIds: List<Long>): SRoom
    suspend fun leave(roomId: Long)
    /** (개발용, 서버 local 프로필에서만) 방을 처음 상태로 돌린다. */
    suspend fun resetRoom(roomId: Long)

    /** WebSocket 을 열고, 끊기면 1·2·4…30초 간격으로 다시 붙는다. */
    fun connect()
    fun disconnect()
    /** 열려 있지 않으면 false. */
    fun send(frame: JSONObject): Boolean
}

/** OkHttp 로 붙는 실제 구현. [base] 예: http://10.0.2.2:8080. [log] 는 끊김 · 읽기 실패 기록(앱은 Logcat). */
class OkHttpRemote(
    private val base: String,
    private val scope: CoroutineScope,
    private val client: OkHttpClient = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build(),
    private val log: (String) -> Unit = {},
) : RemoteApi {

    private val _events = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<RemoteEvent> = _events.asSharedFlow()
    override var token: String? = null

    private var ws: WebSocket? = null
    private var open = false
    private var wanted = false
    private var retryJob: Job? = null
    private var backoffMs = 1_000L

    // ───── REST ─────

    override suspend fun login(loginId: String, password: String): RemoteLogin = try {
        val body = JSONObject().put("loginId", loginId).put("password", password)
        val (code, json) = call("POST", "/api/auth/login", body, auth = false)
        when (code) {
            200 -> RemoteLogin.Ok(json!!.getString("token"), Wire.user(json.getJSONObject("me")))
            401 -> RemoteLogin.Bad(json?.optJSONObject("error")?.let { if (it.has("left")) it.getInt("left") else null })
            423 -> RemoteLogin.Locked
            else -> RemoteLogin.Failed("서버 오류($code)")
        }
    } catch (e: IOException) {
        RemoteLogin.Failed("서버에 연결하지 못했어요")
    }

    override suspend fun logout() {
        runCatching { call("POST", "/api/auth/logout", JSONObject()) }
    }

    override suspend fun me(): SUser = Wire.user(ok("GET", "/api/me") as JSONObject)

    override suspend fun friends(): List<SFriend> = (ok("GET", "/api/friends") as JSONArray).let { a -> (0 until a.length()).map { Wire.friend(a.getJSONObject(it)) } }

    override suspend fun rooms(): List<SRoom> = (ok("GET", "/api/rooms") as JSONArray).let { a -> (0 until a.length()).map { Wire.room(a.getJSONObject(it)) } }

    override suspend fun messages(roomId: Long, after: Long?): List<SMessage> {
        val q = if (after != null) "?after=$after" else ""
        val a = (ok("GET", "/api/rooms/$roomId/messages$q") as JSONObject).getJSONArray("messages")
        return (0 until a.length()).map { Wire.message(a.getJSONObject(it)) }
    }

    override suspend fun places(roomId: Long): List<SPlace> = placeList(ok("GET", "/api/rooms/$roomId/places") as JSONArray)

    override suspend fun addPlace(roomId: Long, place: SPlace): List<SPlace> =
        placeList(ok("POST", "/api/rooms/$roomId/places", Wire.placeJson(place)) as JSONArray)

    override suspend fun createRoom(name: String, memberIds: List<Long>): SRoom =
        Wire.room(ok("POST", "/api/rooms", JSONObject().put("name", name).put("memberIds", JSONArray(memberIds))) as JSONObject)

    override suspend fun invite(roomId: Long, userIds: List<Long>): SRoom =
        Wire.room(ok("POST", "/api/rooms/$roomId/members", JSONObject().put("userIds", JSONArray(userIds))) as JSONObject)

    override suspend fun leave(roomId: Long) {
        ok("DELETE", "/api/rooms/$roomId/members/me")
    }

    override suspend fun resetRoom(roomId: Long) {
        ok("POST", "/api/dev/rooms/$roomId/reset")
    }

    private fun placeList(a: JSONArray) = (0 until a.length()).map { Wire.place(a.getJSONObject(it)) }

    /** 200 이 아니면 예외(401은 [RemoteUnauthorized]). JSONObject 또는 JSONArray. */
    private suspend fun ok(method: String, path: String, body: JSONObject? = null): Any {
        val (code, raw) = callRaw(method, path, body, auth = true)
        if (code == 401) throw RemoteUnauthorized()
        if (code in 400..499) {
            val e = runCatching { JSONObject(raw).getJSONObject("error") }.getOrNull()
            if (e != null) throw RemoteRejected(e.optString("code"), e.optString("message"))
        }
        if (code !in 200..299) throw IOException("서버 오류($code) $path")
        if (raw.isBlank()) return JSONObject()
        return if (raw.trimStart().startsWith("[")) JSONArray(raw) else JSONObject(raw)
    }

    private suspend fun call(method: String, path: String, body: JSONObject?, auth: Boolean = true): Pair<Int, JSONObject?> {
        val (code, raw) = callRaw(method, path, body, auth)
        return code to raw.takeIf { it.isNotBlank() && it.trimStart().startsWith("{") }?.let(::JSONObject)
    }

    private suspend fun callRaw(method: String, path: String, body: JSONObject?, auth: Boolean): Pair<Int, String> = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(base + path)
        if (auth) token?.let { b.header("Authorization", "Bearer $it") }
        val rb = body?.toString()?.toRequestBody(JSON_TYPE)
        b.method(method, if (method == "GET" || (method == "DELETE" && rb == null)) null else rb ?: "".toRequestBody(JSON_TYPE))
        client.newCall(b.build()).execute().use { it.code to (it.body?.string().orEmpty()) }
    }

    // ───── WebSocket ─────

    override fun connect() {
        wanted = true
        if (ws == null) open()
    }

    override fun disconnect() {
        wanted = false
        retryJob?.cancel()
        ws?.close(1000, "bye")
        ws = null
        open = false
    }

    override fun send(frame: JSONObject): Boolean = open && ws?.send(frame.toString()) == true

    private fun open() {
        val t = token ?: return
        val req = Request.Builder().url(base.replaceFirst("http", "ws") + "/ws").header("Authorization", "Bearer $t").build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                open = true
                backoffMs = 1_000L
                _events.tryEmit(RemoteEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { parse(JSONObject(text)) }
                    .onSuccess { e -> if (e != null) _events.tryEmit(e) }
                    .onFailure { log("프레임 읽기 실패: $text ($it)") }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log("WebSocket 끊김: ${t.message}")
                dropped(webSocket)
            }
        })
    }

    private fun dropped(socket: WebSocket) {
        if (socket !== ws) return
        val was = open
        open = false
        ws = null
        if (was) _events.tryEmit(RemoteEvent.Disconnected)
        if (!wanted) return
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            if (wanted && ws == null) open()
        }
    }

    private fun parse(o: JSONObject): RemoteEvent? {
        val ref = if (o.has("ref") && !o.isNull("ref")) o.getString("ref") else null
        return when (o.optString("type")) {
            "ack" -> RemoteEvent.Ack(ref, Wire.message(o.getJSONObject("message")))
            "message.new" -> RemoteEvent.New(Wire.message(o.getJSONObject("message")))
            "message.updated" -> RemoteEvent.Updated(Wire.message(o.getJSONObject("message")))
            "route.live" -> RemoteEvent.Live(o.getLong("roomId"), o.getLong("messageId"), Wire.live(o.getJSONObject("live")))
            "read" -> RemoteEvent.Read(o.getLong("roomId"), o.getLong("userId"), o.getLong("msgId"))
            "error" -> RemoteEvent.Error(ref, o.optString("code"), o.optString("message"))
            "room.updated" -> RemoteEvent.RoomUpdated(Wire.room(o.getJSONObject("room")))
            "room.removed" -> RemoteEvent.RoomRemoved(o.getLong("roomId"))
            "room.cleared" -> RemoteEvent.RoomCleared(o.getLong("roomId"))
            else -> null
        }
    }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
