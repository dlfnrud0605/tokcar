package com.example.tokcar.server.domain;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageLocationRepository extends JpaRepository<MessageLocation, Long> {
    /** 보낸 지 오래된 현재 위치 좌표를 지운다. 지운 수. */
    @Modifying
    @Query("delete from MessageLocation l where l.messageId in"
            + " (select m.id from Message m where m.kind = com.example.tokcar.server.domain.Message.Kind.LOCATION"
            + " and m.createdAt < :cut)")
    int deleteSentBefore(@Param("cut") Instant cut);

    /** 방의 현재 위치 좌표를 모두 지운다(개발용 방 초기화). 지운 수. */
    @Modifying
    @Query("delete from MessageLocation l where l.messageId in (select m.id from Message m where m.roomId = :roomId)")
    int deleteAllOf(@Param("roomId") Long roomId);
}
