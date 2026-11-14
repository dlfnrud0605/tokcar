package com.example.tokcar.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 대화 한 줄. 모든 종류가 같이 쓰는 칸만 둔다.
 * 종류별 내용은 {@link MessageLocation}(LOCATION) · {@link RouteShare}(ROUTE)에 같은 id 로.
 */
@Entity
@Table(name = "messages")
public class Message {

    public enum Kind { TEXT, LOCATION, ROUTE, SYSTEM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    /** 시스템 안내면 null. */
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    /** TEXT · SYSTEM 의 글. */
    @Column(length = 1000)
    private String text;

    @Column(nullable = false)
    private Instant createdAt;

    protected Message() {
    }

    public Message(Long roomId, Long senderId, Kind kind, String text) {
        this(roomId, senderId, kind, text, Instant.now());
    }

    public Message(Long roomId, Long senderId, Kind kind, String text, Instant createdAt) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.kind = kind;
        this.text = text;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public Long getSenderId() { return senderId; }
    public Kind getKind() { return kind; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
