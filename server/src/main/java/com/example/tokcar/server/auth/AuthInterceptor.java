package com.example.tokcar.server.auth;

import com.example.tokcar.server.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** /api/** 요청의 토큰을 확인하고, 사용자 id 를 요청 속성 {@link #UID} 로 넘긴다. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String UID = "uid";
    public static final String TOKEN = "token";

    private final TokenStore tokens;

    public AuthInterceptor(TokenStore tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String token = TokenStore.fromHeader(req.getHeader(HttpHeaders.AUTHORIZATION));
        long uid = tokens.userOf(token).orElseThrow(ApiException::unauthorized);
        req.setAttribute(UID, uid);
        req.setAttribute(TOKEN, token);
        return true;
    }
}
