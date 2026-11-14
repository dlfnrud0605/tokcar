package com.example.tokcar.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 「이 방에서 간 곳」 한 줄: 이 방에서 경로 안내를 시작한 곳. 방마다 최대 {@code MAX}개.
 * 순서는 id 로 본다(새로 넣으면 가장 큼, 이미 있는 곳을 다시 안내하면 지우고 다시 넣음).
 */
@Entity
@Table(name = "room_places")
public class RoomPlace {

    public static final int MAX = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 120)
    private String addr;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    protected RoomPlace() {
    }

    public RoomPlace(Long roomId, String name, String addr, double lat, double lon) {
        this.roomId = roomId;
        this.name = name;
        this.addr = addr;
        this.lat = lat;
        this.lon = lon;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddr() { return addr; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
}
