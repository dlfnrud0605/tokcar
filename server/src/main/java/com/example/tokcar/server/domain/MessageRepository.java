package com.example.tokcar.server.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {
    /** 최신 것부터(앱에 줄 땐 뒤집는다). */
    @Query("select m from Message m where m.roomId = :roomId order by m.id desc")
    List<Message> latest(@Param("roomId") Long roomId, Pageable page);

    @Query("select m from Message m where m.roomId = :roomId and m.id < :before order by m.id desc")
    List<Message> before(@Param("roomId") Long roomId, @Param("before") Long before, Pageable page);

    @Query("select m from Message m where m.roomId = :roomId and m.id > :after order by m.id asc")
    List<Message> after(@Param("roomId") Long roomId, @Param("after") Long after, Pageable page);

    /** 방의 모든 메시지 id(개발용 방 초기화에서 실시간 값을 비우는 데 씀). */
    @Query("select m.id from Message m where m.roomId = :roomId")
    List<Long> idsOf(@Param("roomId") Long roomId);

    /** 방의 메시지를 모두 지운다(개발용 방 초기화). 딸린 위치 · 경로 공유를 먼저 지운 뒤에 부른다. 지운 수. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Message m where m.roomId = :roomId")
    int deleteAllOf(@Param("roomId") Long roomId);

    /** 나 기준 안 읽은 수: 내가 읽은 곳 뒤의, 남이 보낸 메시지(시스템 안내 제외). */
    @Query("select count(m) from Message m where m.roomId = :roomId and m.id > :readUpTo"
            + " and m.senderId is not null and m.senderId <> :me")
    long unread(@Param("roomId") Long roomId, @Param("readUpTo") long readUpTo, @Param("me") Long me);
}
