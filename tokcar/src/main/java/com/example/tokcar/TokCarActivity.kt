package com.example.tokcar

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tokcar.core.Session
import com.example.tokcar.core.TokStore
import com.example.tokcar.data.FakeServer
import com.example.tokcar.data.ServerReverseGeocoder
import com.example.tokcar.data.SeedLoader
import com.example.tokcar.navi.DemoNaviGateway
import com.example.tokcar.navi.HybridNaviGateway
import com.example.tokcar.navi.PleosNaviGateway
import com.example.tokcar.remote.OkHttpRemote
import com.example.tokcar.speech.PleosSpeaker
import com.example.tokcar.speech.PleosVoiceInput
import com.example.tokcar.ui.TokCarRoot
import com.example.tokcar.ui.theme.TokTheme
import com.example.tokcar.vehicle.VehicleGearSource

/**
 * TokCar — 집에서 기다리는 가족에게 도착 정보를 알려 주는 차량용 앱. 화면 기준은 화면 시안(하늘색 테마).
 * 서버는 가족 서버(BuildConfig.TOKCAR_SERVER, 기본 http://10.0.2.2:8080)에 항상 연결한다. 서버가 꺼져 있으면 로그인할 수 없다(더미 모드 없음; assets/dummy/seed.json 은 화면 기본 문구용). 내비는 기본이 실제 내비(Pleos NaviHelper)이고 연결에 실패하면 데모 GPS로 바뀐다. 설정의 개발용 항목에서 직접 바꿀 수도 있다.
 * 창 방향은 고정하지 않는다(어떤 크기든 창 전체를 채움).
 */
class TokCarActivity : ComponentActivity() {
    private val vm: TokViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TokTheme { TokCarRoot(vm.store) } }
    }
}

/** 화면이 다시 만들어져도(회전 등) 상태가 이어지도록 [TokStore]를 들고 있다. 로그인 상태는 기기에 저장해 다시 켜도 유지된다. */
class TokViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("tokcar", Context.MODE_PRIVATE)
    private val server = FakeServer(SeedLoader.load(app), viewModelScope) { Log.d("FakeServer", it) }
    private val remote = OkHttpRemote(BuildConfig.TOKCAR_SERVER.trimEnd('/'), viewModelScope) { m -> Log.d("TokRemote", m) }

    val store = TokStore(
        scope = viewModelScope,
        server = server,
        gatewayFactory = { real ->
            when {
                !real -> DemoNaviGateway(app, viewModelScope)
                // 시연용: 실제 내비에 연결하되 남은 시간은 움직이는 데모 이동 값으로(설정의 개발용 스위치)
                prefs.getBoolean(KEY_DEMO_MOVE, true) -> HybridNaviGateway(app, viewModelScope, PleosNaviGateway(app, viewModelScope))
                else -> PleosNaviGateway(app, viewModelScope)
            }
        },
        voice = PleosVoiceInput(app) { Log.d("PleosVoice", it) },
        speaker = PleosSpeaker(app) { Log.d("PleosSpeech", it) },
        gearSource = VehicleGearSource(app),
        initialReal = prefs.getBoolean(KEY_REAL, true),    // 처음에는 실제 내비로 연결(못 붙으면 데모 GPS로 바뀜)
        onNaviModeChanged = { prefs.edit().putBoolean(KEY_REAL, it).apply() },
        initialDemoMove = prefs.getBoolean(KEY_DEMO_MOVE, true),    // 처음에는 내비는 실제로 연결하고 남은 시간 · 위치는 데모 이동값(에뮬레이터 내비는 차가 안 움직여서). 실차에서는 설정에서 끈다
        onDemoMoveChanged = { prefs.edit().putBoolean(KEY_DEMO_MOVE, it).apply() },
        session = loadSession(),
        onSessionChanged = ::saveSession,
        remote = remote,
        remoteToken = prefs.getString(KEY_TOKEN, null),
        onTokenChanged = { t -> prefs.edit().apply { if (t == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, t) }.apply() },
        // 좌표 → 주소는 가족 서버가 네이버를 대신 불러 준다(서버에만 비밀 키가 있음). 서버에 키가 없으면 내비가 준 동 주소를 그대로 쓴다.
        addressLookup = ServerReverseGeocoder(BuildConfig.TOKCAR_SERVER, { remote.token }, log = { m -> Log.d("TokAddr", m) }),
    )

    private fun loadSession(): Session? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        return Session(id, prefs.getString(KEY_NAME, "").orEmpty(), prefs.getString(KEY_PHONE, "").orEmpty(), prefs.getString(KEY_MASK, "").orEmpty())
    }

    private fun saveSession(s: Session?) {
        prefs.edit().apply {
            if (s == null) listOf(KEY_ID, KEY_NAME, KEY_PHONE, KEY_MASK).forEach(::remove)
            else putString(KEY_ID, s.id).putString(KEY_NAME, s.name).putString(KEY_PHONE, s.phone).putString(KEY_MASK, s.phoneMask)
        }.apply()
    }

    override fun onCleared() {
        store.release()
    }

    private companion object {
        const val KEY_REAL = "naviReal"
        const val KEY_DEMO_MOVE = "demoMove"
        const val KEY_ID = "sessionId"
        const val KEY_NAME = "sessionName"
        const val KEY_PHONE = "sessionPhone"
        const val KEY_MASK = "sessionMask"
        const val KEY_TOKEN = "serverToken"
    }
}
