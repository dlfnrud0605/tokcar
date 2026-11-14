package com.example.tokcar.server.chat;

import com.example.tokcar.server.domain.Message;
import com.example.tokcar.server.domain.RouteShare;
import com.example.tokcar.server.domain.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.List;

/** 앱과 주고받는 JSON 모양(API 명세 2절). */
public final class Views {

    private Views() {
    }

    public record UserView(Long id, String name, String profileImageUrl) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getName(), imageUrl(u.getProfileImageKey()));
        }
    }

    /** 로그인한 나. 로그인 아이디는 내 것만 보낸다. */
    public record Me(Long id, String loginId, String name, String profileImageUrl) {
        public static Me of(User u) {
            return new Me(u.getId(), u.getLoginId(), u.getName(), imageUrl(u.getProfileImageKey()));
        }
    }

    /** 친구 목록 한 줄. 전화번호는 보내지 않는다. */
    public record FriendView(Long id, String name, String profileImageUrl, String statusMsg) {
        public static FriendView of(User u) {
            return new FriendView(u.getId(), u.getName(), imageUrl(u.getProfileImageKey()), u.getStatusMsg());
        }
    }

    /** 현재 위치 · 출발 · 목적지 · 이 방에서 간 곳이 모두 이 모양. */
    public record Place(String name, String addr, Double lat, Double lon) {
    }

    public enum Level { FAST, MODERATE, SLOW, HEAVY }

    /** 정체 구간 한 칸. JSON 은 [from, to, "FAST"] 배열. from · to 는 경로 전체를 0~1로 본 위치. */
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"from", "to", "level"})
    public record TrafficSeg(double from, double to, Level level) {
    }

    /** 경로 공유의 실시간 값. 서버 메모리에만 있다. traffic 이 null 이면 정보 없음, 빈 목록이면 정체 없음. */
    public record Live(Double carLat, Double carLon, Integer remainSec, Integer pct, Instant etaAt,
                       List<TrafficSeg> traffic, Instant updatedAt) {
    }

    public record RouteView(RouteShare.State state, Place from, Place dest, Instant endedAt, Live live) {
    }

    public record MessageView(Long id, Long roomId, Long senderId, Message.Kind kind, Instant createdAt,
                              String text, Place location, RouteView route) {
    }

    public record ReadMark(Long userId, long msgId) {
    }

    public record RoomView(Long id, String name, List<UserView> members, List<ReadMark> readUpTo,
                           MessageView lastMessage, long unread) {
    }

    public record RoomPlaceView(Long id, String name, String addr, double lat, double lon) {
    }

    public record MessagesPage(List<MessageView> messages, boolean hasMore) {
    }

    public record LoginResult(String token, Me me) {
    }

    /** 사진 키 → 앱이 받을 주소. 1단계는 올리기가 없어 늘 null. */
    static String imageUrl(String key) {
        return key == null ? null : "/files/" + key;
    }
}
