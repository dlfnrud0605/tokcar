package com.example.tokcar.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** 내 친구 목록 한 줄. 한쪽 방향(카카오톡처럼 내가 추가하면 내 목록에만 생김). */
@Entity
@Table(name = "friendships")
public class Friendship {

    @Embeddable
    public record Key(Long userId, Long friendId) {
    }

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private Instant createdAt;

    protected Friendship() {
    }

    public Friendship(Long userId, Long friendId) {
        this.id = new Key(userId, friendId);
        this.createdAt = Instant.now();
    }

    public Long getFriendId() { return id.friendId(); }
}
