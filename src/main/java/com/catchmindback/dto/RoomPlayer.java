package com.catchmindback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomPlayer {
  private String nickname;
  private boolean isReady; // 준비 완료 여부
  private boolean isHost;  // 방장 여부
}