package com.catchmindback.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

@Getter
@Setter
public class RoomState {
  private String roomId;
  private List<RoomPlayer> players = new ArrayList<>();
  private String currentDrawer;
  private String currentAnswer;
  private List<String> usedWords = new ArrayList<>();
  private Set<String> skipVotes = new HashSet<>();
  private ScheduledFuture<?> timerTask;
  private boolean isPlaying; // 현재 게임 중인지 대기 중인지 상태
}
