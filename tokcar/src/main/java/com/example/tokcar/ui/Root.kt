package com.example.tokcar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tokcar.core.TokStore
import com.example.tokcar.ui.theme.Dims
import com.example.tokcar.ui.theme.K

/**
 * 앱 전체. 창이 넓으면(1400dp 이상) 왼쪽 목록 + 오른쪽 대화방 두 칸, 아니면 한 칸(대화방을 열면 대화방만).
 * 팝업·대화상대 서랍·+ 메뉴·토스트는 맨 위에 겹친다.
 */
@Composable
fun TokCarRoot(store: TokStore) {
    val s by store.ui.collectAsStateWithLifecycle()
    BackHandler(enabled = s.modal != null || s.drawer || s.plusOpen || (s.signedIn && s.inChat) || (!s.signedIn && s.login.step != com.example.tokcar.core.LoginStep.WELCOME)) {
        when {
            s.modal != null -> store.closeModal()
            s.drawer -> store.closeDrawer()
            s.plusOpen -> store.closePlus()
            !s.signedIn -> store.loginBack()
            else -> store.back()
        }
    }
    // 화면 어디든 누르면 키보드를 내린다(눌린 곳의 동작은 그대로 진행: 감시만 하고 이벤트는 가로채지 않음).
    // 입력 상자 안은 [keepKeyboard]로 등록돼 있어서 누르면 키보드가 유지된다.
    val keep = remember { KeepRegistry() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    CompositionLocalProvider(LocalKeepRegistry provides keep) {
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Press) {
                            val p = e.changes.firstOrNull()?.position
                            if (p != null && !keep.contains(p)) { focus.clearFocus(); keyboard?.hide() }
                        }
                    }
                }
            }
            .background(K.bg).safeDrawingPadding(),
    ) {
        val wide = maxWidth >= 1400.dp
        val room = s.room(s.roomId)?.takeIf { s.inChat }
        when {
            !s.signedIn -> LoginScreen(s, store)
            wide -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(760.dp).fillMaxHeight()) { HomeScreen(s, store, room?.id) }
                Box(Modifier.width(2.dp).fillMaxHeight().background(K.line))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (room != null) ChatRoom(s, store, room, showBack = true) else EmptyRoomPane()
                }
            }
            room != null -> ChatRoom(s, store, room)
            else -> HomeScreen(s, store)
        }

        // 대화상대 서랍(오른쪽에서)
        // 먼저 화면이 흐려지고(어두운 막이 제자리에서 서서히 나타남), 그다음 서랍만 오른쪽에서 밀려 나온다.
        // (막과 서랍을 한 덩어리로 밀면 흐림까지 같이 쓸려 나와 어색했음)
        val drawerOpen = s.drawer && room != null
        AnimatedVisibility(drawerOpen, enter = fadeIn(tween(180)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f)).tapQuiet(store::closeDrawer))
        }
        AnimatedVisibility(
            drawerOpen, modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(260, delayMillis = 120)) { it }, exit = slideOutHorizontally(tween(220)) { it },
        ) {
            room?.let { MembersDrawer(s, store, it) }
        }
        ModalLayer(s, store)
        s.toast?.let { Toast(it, Modifier.align(Alignment.BottomCenter).padding(bottom = Dims.barH + 140.dp)) }
    }
    }
}
