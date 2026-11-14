package com.example.tokcar.server.chat;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 「현재 위치」 좌표는 보낸 지 1년이 지나면 지운다(하루 한 번, 새벽 4시 30분). */
@Component
public class LocationCleanup {

    public static final Duration KEEP = Duration.ofDays(365);
    private static final Logger log = LoggerFactory.getLogger(LocationCleanup.class);

    private final ChatService chat;

    public LocationCleanup(ChatService chat) {
        this.chat = chat;
    }

    @Scheduled(cron = "0 30 4 * * *")
    public int run() {
        int n = chat.deleteLocationsSentBefore(Instant.now().minus(KEEP));
        if (n > 0) log.info("보낸 지 1년 지난 현재 위치 {}건을 지움", n);
        return n;
    }
}
