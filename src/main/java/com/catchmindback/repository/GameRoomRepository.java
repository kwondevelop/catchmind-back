package com.catchmindback.repository;

import com.catchmindback.entity.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRoomRepository extends JpaRepository<GameRoom, String> {
}