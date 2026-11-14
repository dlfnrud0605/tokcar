package com.example.tokcar.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** 누가 어느 방에 있는지 + 어디까지 읽었는지(메시지마다 읽음을 따로 두지 않음). */
@Entity
@Table(name = "room_members")
public class RoomMember {

    @Embeddable
    public record Key(Long roomId, Long userId) {
    }

    @EmbeddedId
    private Key id;

    /** 여기까지 읽음(메시지 id). 아무것도 안 읽었으면 0. */
    @Column(nullable = false)
    private long lastReadMsgId;

    @Column(nullable = false)
    private Instant joinedAt;

    protected RoomMember() {
    }

    public RoomMember(Long roomId, Long userId) {
        this.id = new Key(roomId, userId);
        this.joinedAt = Instant.now();
    }

    public Long getRoomId() { return id.roomId(); }
    public Long getUserId() { return id.userId(); }
    public long getLastReadMsgId() { return lastReadMsgId; }

    /** 아무것도 안 읽은 처음 상태로(개발용 방 초기화: 메시지를 모두 지운 뒤). */
    public void resetRead() {
        lastReadMsgId = 0;
    }

    /** 지금 값보다 클 때만 올린다. 올렸으면 true. */
    public boolean readUpTo(long msgId) {
        if (msgId <= lastReadMsgId) return false;
        lastReadMsgId = msgId;
        return true;
    }
}
