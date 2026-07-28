package com.catchmindback.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class GameRoom {
  @Id
  private String roomId; // UUID 형태의 고유 방 번호 (ex. room-1234)
  private String roomName; // 로비에 표시될 방 제목
  private int maxPlayers = 8; // 최대 인원 제한 (기본 8명)

  @Transient
  private int currentPlayers = 0; // 현재 접속 인원 (메모리 관리용)

  @Transient
  private boolean isPlaying = false; // 게임 진행 중 여부 (메모리 관리용)
}