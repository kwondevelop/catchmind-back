package com.catchmindback.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Player {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // DB 자동 생성 PK

  @Column(nullable = false, unique = true)
  private String nickname; // 유저 닉네임 (중복 방지)
}