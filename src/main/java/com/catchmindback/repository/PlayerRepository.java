package com.catchmindback.repository;

import com.catchmindback.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {
  // 닉네임 중복 검사를 위한 메서드
  Optional<Player> findByNickname(String nickname);
}