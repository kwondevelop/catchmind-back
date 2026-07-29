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
  
  // 되돌리기 상태 반영
  private String imageState;

  // Getter & Setter
  public String getImageState() {
    return imageState;
  }

  public void setImageState(String imageState) {
    this.imageState = imageState;
  }
}