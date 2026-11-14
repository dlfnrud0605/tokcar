package com.example.tokcar.server.ws;

import com.example.tokcar.server.chat.Views.Live;
import com.example.tokcar.server.chat.Views.Place;
import com.example.tokcar.server.chat.Views.TrafficSeg;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/** 앱 → 서버 WebSocket 프레임(API 명세 4절). type · ref 는 처리하는 쪽에서 따로 읽는다. */
final class Frames {

    private Frames() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TextSend(Long roomId, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LocationSend(Long roomId, Place location) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RouteStart(Long roomId, Place from, Place dest, Live live) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RouteLive(Long roomId, Double carLat, Double carLon, Integer remainSec, Integer pct, Instant etaAt,
                     List<TrafficSeg> traffic) {
        Live toLive() {
            return new Live(carLat, carLon, remainSec, pct, etaAt, traffic, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RouteDest(Long roomId, Place dest) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RouteEnd(Long roomId, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Read(Long roomId, Long msgId) {
    }
}
