package com.example.tokcar.server.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendshipRepository extends JpaRepository<Friendship, Friendship.Key> {
    List<Friendship> findByIdUserId(Long userId);
}
