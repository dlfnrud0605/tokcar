package com.example.tokcar.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 대화방. */
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false)
    private Long createdBy;

    /** 방 목록 정렬용(최근 대화 순). 메시지를 저장할 때 같이 바꾼다. */
    private Long lastMsgId;

    @Column(nullable = false)
    private Instant createdAt;

    protected Room() {
    }

    public Room(String name, Long createdBy) {
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getLastMsgId() { return lastMsgId; }

    public void setLastMsgId(Long lastMsgId) { this.lastMsgId = lastMsgId; }
}
