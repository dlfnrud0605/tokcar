package com.example.tokcar.server.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
class WsConfig implements WebSocketConfigurer {

    /** 앱은 25초마다 ping 을 보낸다. 이만큼 아무것도 안 오면 끊긴 것으로 보고 닫는다. */
    static final long IDLE_MS = 60_000;

    private final ChatSocketHandler handler;
    private final WsAuth auth;

    WsConfig(ChatSocketHandler handler, WsAuth auth) {
        this.handler = handler;
        this.auth = auth;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 앱(차 · 폰)만 붙는다. 브라우저 Origin 제한은 웹 화면이 생기면 그때 좁힌다.
        registry.addHandler(handler, "/ws").addInterceptors(auth).setAllowedOriginPatterns("*");
    }

    @Bean
    ServletServerContainerFactoryBean webSocketContainer() {
        var c = new ServletServerContainerFactoryBean();
        c.setMaxSessionIdleTimeout(IDLE_MS);
        c.setMaxTextMessageBufferSize(64 * 1024);
        return c;
    }
}
