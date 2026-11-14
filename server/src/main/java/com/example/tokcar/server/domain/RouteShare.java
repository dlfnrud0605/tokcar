package com.example.tokcar.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 경로 공유 카드의 기록. messages(kind = ROUTE)와 1:1. 시작 · 목적지 변경 · 끝에만 쓴다.
 * 시작 시각은 messages.created_at, 방 · 보낸 사람도 messages 에 있다.
 * 차 위치 · 남은 시간 같은 실시간 값은 DB 에 두지 않는다({@code LiveRoutes}).
 */
@Entity
@Table(name = "route_shares")
public class RouteShare {

    public enum State { ACTIVE, ARRIVED, STOPPED }

    @Id
    private Long messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private State state;

    @Column(nullable = false, length = 120)
    private String fromAddr;
    @Column(nullable = false)
    private double fromLat;
    @Column(nullable = false)
    private double fromLon;

    @Column(nullable = false, length = 60)
    private String destName;
    @Column(nullable = false, length = 120)
    private String destAddr;
    @Column(nullable = false)
    private double destLat;
    @Column(nullable = false)
    private double destLon;

    /** 도착 · 멈춘 시각. 진행 중이면 null. */
    private Instant endedAt;

    protected RouteShare() {
    }

    public RouteShare(Long messageId, String fromAddr, double fromLat, double fromLon,
                      String destName, String destAddr, double destLat, double destLon) {
        this.messageId = messageId;
        this.state = State.ACTIVE;
        this.fromAddr = fromAddr;
        this.fromLat = fromLat;
        this.fromLon = fromLon;
        changeDest(destName, destAddr, destLat, destLon);
    }

    public void changeDest(String name, String addr, double lat, double lon) {
        this.destName = name;
        this.destAddr = addr;
        this.destLat = lat;
        this.destLon = lon;
    }

    public void end(State how) {
        this.state = how;
        this.endedAt = Instant.now();
    }

    public Long getMessageId() { return messageId; }
    public State getState() { return state; }
    public String getFromAddr() { return fromAddr; }
    public double getFromLat() { return fromLat; }
    public double getFromLon() { return fromLon; }
    public String getDestName() { return destName; }
    public String getDestAddr() { return destAddr; }
    public double getDestLat() { return destLat; }
    public double getDestLon() { return destLon; }
    public Instant getEndedAt() { return endedAt; }
}
