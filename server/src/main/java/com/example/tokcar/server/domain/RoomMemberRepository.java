package com.example.tokcar.server.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMember.Key> {
    List<RoomMember> findByIdUserId(Long userId);

    List<RoomMember> findByIdRoomId(Long roomId);
}
