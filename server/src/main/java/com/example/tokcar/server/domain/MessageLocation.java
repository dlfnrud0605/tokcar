package com.example.tokcar.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 「내 위치 보내기」로 보낸 그 순간의 위치. messages(kind = LOCATION)와 1:1. 보낸 지 1년이 지나면 지운다. */
@Entity
@Table(name = "message_locations")
public class MessageLocation {

    @Id
    private Long messageId;

    @Column(nullable = false, length = 60)
    private String placeName;

    @Column(nullable = false, length = 120)
    private String placeAddr;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    protected MessageLocation() {
    }

    public MessageLocation(Long messageId, String placeName, String placeAddr, double lat, double lon) {
        this.messageId = messageId;
        this.placeName = placeName;
        this.placeAddr = placeAddr;
        this.lat = lat;
        this.lon = lon;
    }

    public Long getMessageId() { return messageId; }
    public String getPlaceName() { return placeName; }
    public String getPlaceAddr() { return placeAddr; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
}
