package com.catchmindback.controller;

import com.catchmindback.dto.ChatMessage;
import com.catchmindback.dto.DrawMessage;
import com.catchmindback.dto.RoomPlayer;
import com.catchmindback.service.GameService;
import com.catchmindback.service.LobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class GameController {

  private final SimpMessagingTemplate messagingTemplate;
  private final GameService gameService;
  private final LobbyService lobbyService;

  @MessageMapping("/room/{roomId}/draw")
  public void handleDraw(@DestinationVariable String roomId, DrawMessage message) {
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/draw", message);
  }

  // 게임 시작 (최초 1회만 방장에 의해 호출됨)
  @MessageMapping("/room/{roomId}/start")
  public void startGame(@DestinationVariable String roomId, ChatMessage requestMsg) {
    // 로비 서비스에서 해당 방의 라운드 수를 가져와 게임 서비스로 전달
    int maxRound = lobbyService.getRoomMaxRound(roomId);
    gameService.startNewGame(roomId, maxRound, messagingTemplate);
  }

  // 채팅 및 정답 판정 (정답 시 다음 라운드 자동 스케줄링)
  @MessageMapping("/room/{roomId}/chat")
  public void handleChat(@DestinationVariable String roomId, ChatMessage message) {

    // 출제자가 친 채팅은 정답 판정에서 제외하고 일반 채팅으로 처리
    String currentDrawer = gameService.getCurrentDrawer(roomId);
    if (currentDrawer != null && currentDrawer.equals(message.getSender())) {
      messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", message);
      return;
    }

    // 💡 수정됨: 복잡한 로직을 모두 GameService의 endRound 로 위임!
    if (gameService.isCorrectAnswer(roomId, message.getMessage())) {
      // 정답자 닉네임과 함께 "CORRECT" 상태로 라운드 종료 메서드 호출
      gameService.endRound(roomId, "CORRECT", message.getSender(), messagingTemplate);
    } else {
      // 일반 채팅
      messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", message);
    }
  }

  // 유저 목록을 방 전체에 방송하는 공통 유틸 메서드
  private void broadcastPlayers(String roomId, List<RoomPlayer> players) {
    ChatMessage msg = new ChatMessage();
    msg.setType("PLAYERS");
    msg.setSender("시스템");
    msg.setData(players); // data 필드에 유저 리스트를 통째로 담습니다.
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", msg);
  }

  // 유저 입장 알림
  @MessageMapping("/room/{roomId}/enter")
  public void handleEnter(@DestinationVariable String roomId, ChatMessage message) {
    List<RoomPlayer> players = gameService.playerEnter(roomId, message.getSender());
    broadcastPlayers(roomId, players);
  }

  // 유저 준비(Ready) 알림
  @MessageMapping("/room/{roomId}/ready")
  public void handleReady(@DestinationVariable String roomId, ChatMessage message) {
    List<RoomPlayer> players = gameService.toggleReady(roomId, message.getSender());
    broadcastPlayers(roomId, players);
  }

  // 유저 퇴장 알림
  @MessageMapping("/room/{roomId}/leave")
  public void handleLeave(@DestinationVariable String roomId, ChatMessage message) {
    // 💡 수정됨: 서비스에 messagingTemplate 객체를 함께 전달합니다.
    List<RoomPlayer> players = gameService.playerLeave(roomId, message.getSender(), messagingTemplate);

    // 퇴장 후 남은 인원이 0명이라면? 방 폭파
    if (players != null && players.isEmpty()) {
      gameService.cleanupRoom(roomId); // 1. 메모리에서 방 데이터 싹 청소
      lobbyService.deleteRoom(roomId); // 2. DB에서 방 완전 삭제
    } else if (players != null) {
      // 인원이 남아있다면 남은 사람들에게만 갱신된 명단 방송
      broadcastPlayers(roomId, players);
    }
  }

  // 스킵 투표 알림 수신
  @MessageMapping("/room/{roomId}/skip")
  public void handleSkipVote(@DestinationVariable String roomId, ChatMessage message) {
    gameService.handleSkipVote(roomId, message.getSender(), messagingTemplate);
  }
}