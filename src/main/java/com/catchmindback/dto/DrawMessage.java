package com.catchmindback.dto;

import lombok.Data;

@Data
public class DrawMessage {
  private String type;      // "DRAW" 또는 "CLEAR"
  private String senderId;  // 보낸 유저 ID (에코 방지용)
  private String color;     // 선 색상
  private int width;        // 선 두께
  private double startX;
  private double startY;
  private double endX;
  private double endY;
}