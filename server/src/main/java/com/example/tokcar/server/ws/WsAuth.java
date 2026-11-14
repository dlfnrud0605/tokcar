package com.example.tokcar.server.ws;

import com.example.tokcar.server.auth.TokenStore;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** /ws 연결 전에 Authorization 헤더의 토큰을 확인한다. 틀리면 HTTP 401로 연결을 받지 않는다. */
@Component
class WsAuth implements HandshakeInterceptor {

    static final String UID = "uid";
    static final String TOKEN = "token";

    private final TokenStore tokens;

    WsAuth(TokenStore tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest req, ServerHttpResponse res, WebSocketHandler handler,
                                   Map<String, Object> attrs) {
        String token = TokenStore.fromHeader(req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        var uid = tokens.userOf(token);
        if (uid.isEmpty()) {
            res.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attrs.put(UID, uid.get());
        attrs.put(TOKEN, token);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res, WebSocketHandler handler,
                               Exception exception) {
    }
}
