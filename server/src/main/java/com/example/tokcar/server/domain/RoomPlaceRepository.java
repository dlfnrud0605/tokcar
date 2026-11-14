package com.example.tokcar.server.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomPlaceRepository extends JpaRepository<RoomPlace, Long> {
    List<RoomPlace> findByRoomIdOrderByIdDesc(Long roomId);

    Optional<RoomPlace> findByRoomIdAndNameAndAddr(Long roomId, String name, String addr);
}
