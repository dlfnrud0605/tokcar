package com.example.tokcar.server.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteShareRepository extends JpaRepository<RouteShare, Long> {
    /** 이 방에서 이 사람이 진행 중인 경로 공유. */
    @Query("select r from RouteShare r join Message m on m.id = r.messageId"
            + " where m.roomId = :roomId and m.senderId = :senderId"
            + " and r.state = com.example.tokcar.server.domain.RouteShare.State.ACTIVE")
    Optional<RouteShare> findActive(@Param("roomId") Long roomId, @Param("senderId") Long senderId);

    /** 방의 경로 공유 기록을 모두 지운다(개발용 방 초기화). 지운 수. */
    @Modifying
    @Query("delete from RouteShare r where r.messageId in (select m.id from Message m where m.roomId = :roomId)")
    int deleteAllOf(@Param("roomId") Long roomId);
}
