package com.example.tokcar.server.auth;

import com.example.tokcar.server.chat.Views.LoginResult;
import com.example.tokcar.server.chat.Views.Me;
import com.example.tokcar.server.ws.Sessions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    public record LoginRequest(String loginId, String password) {
    }

    private final AuthService auth;
    private final TokenStore tokens;
    private final Sessions sessions;

    public AuthController(AuthService auth, TokenStore tokens, Sessions sessions) {
        this.auth = auth;
        this.tokens = tokens;
        this.sessions = sessions;
    }

    @PostMapping("/api/auth/login")
    public LoginResult login(@RequestBody LoginRequest req) {
        return auth.login(req.loginId(), req.password());
    }

    /** 토큰을 버리고, 그 토큰으로 열린 WebSocket 도 닫는다. */
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@RequestAttribute(AuthInterceptor.TOKEN) String token) {
        tokens.revoke(token);
        sessions.closeByToken(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/me")
    public Me me(@RequestAttribute(AuthInterceptor.UID) long uid) {
        return auth.me(uid);
    }
}
