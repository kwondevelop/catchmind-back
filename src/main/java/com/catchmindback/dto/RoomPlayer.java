package com.catchmindback.dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomPlayer {
  private String nickname;
  private boolean ready;
  private boolean host;
  private int score = 0; // 방금 추가된 점수 필드

  public RoomPlayer(String nickname, boolean ready, boolean host) {
    this.nickname = nickname;
    this.ready = ready;
    this.host = host;
    this.score = 0; // 새 플레이어는 기본 0점
  }
}