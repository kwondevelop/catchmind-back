package com.catchmindback.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameRoom {
  @Id
  private String roomId;
  private String roomName;
  private int maxPlayers = 8;

  // 모달에서 설정한 라운드 수를 저장할 필드 추가
  private int maxRound = 10;

  private int currentPlayers = 0;
  private boolean isPlaying = false;
}