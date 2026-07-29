package com.catchmindback.dto;

import lombok.Data;

@Data
public class ChatMessage {
  private String type;    // 메시지 타입 ("CHAT", "SYSTEM", "START", "TIME" 등)
  private String sender;  // 보낸 사람 (닉네임 또는 "시스템")
  private String message; // 채팅 내용, 정답 단어, 남은 시간 등
  private String drawerId; // 출제자 닉네임
  private Object data; // 유저 목록(List) 등 복잡한 데이터를 담아 보낼 수 있는 만능 주머니

  // 추가됨: 현재 라운드 및 최대 라운드 정보
  private int currentRound;
  private int maxRound;
}