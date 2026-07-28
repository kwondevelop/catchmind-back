package com.catchmindback.controller;

import com.catchmindback.dto.ChatMessage;
import com.catchmindback.dto.DrawMessage;
import com.catchmindback.dto.RoomPlayer;
import com.catchmindback.service.GameService;
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

  @MessageMapping("/room/{roomId}/draw")
  public void handleDraw(@DestinationVariable String roomId, DrawMessage message) {
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/draw", message);
  }

  // 게임 시작 (최초 1회만 방장에 의해 호출됨)
  @MessageMapping("/room/{roomId}/start")
  public void startGame(@DestinationVariable String roomId, ChatMessage requestMsg) {
    gameService.startNewGame(roomId, messagingTemplate);
  }

  // 채팅 및 정답 판정 (정답 시 다음 라운드 자동 스케줄링 추가)
  @MessageMapping("/room/{roomId}/chat")
  public void handleChat(@DestinationVariable String roomId, ChatMessage message) {

    // 출제자가 친 채팅은 정답 판정에서 제외하고 일반 채팅으로 처리
    String currentDrawer = gameService.getCurrentDrawer(roomId);
    if (currentDrawer != null && currentDrawer.equals(message.getSender())) {
      messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", message);
      return;
    }

    if (gameService.isCorrectAnswer(roomId, message.getMessage())) {
      // 정답을 맞췄으므로 타이머 중지
      gameService.stopTimer(roomId);

      // 💡 수정됨: 정답 단어를 안전하게 꺼내어 시스템 메시지에 포함시킴
      String revealedAnswer = gameService.processCorrectAnswer(roomId);

      // 정답 시스템 메시지 전송 (정답 단어 안내 추가)
      ChatMessage systemMsg = new ChatMessage();
      systemMsg.setType("SYSTEM");
      systemMsg.setSender("시스템");
      systemMsg.setMessage(message.getSender() + "님이 정답을 맞췄습니다! 정답은 [" + revealedAnswer + "] 입니다. (3초 뒤 다음 라운드)");
      messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", systemMsg);

      // 캔버스 자동 초기화
      gameService.clearRoomCanvas(roomId, messagingTemplate);

      // 3초 뒤에 다음 사람의 턴으로 새 라운드 자동 시작
      gameService.scheduleNextRound(roomId, messagingTemplate, 3);
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
    List<RoomPlayer> players = gameService.playerLeave(roomId, message.getSender());
    broadcastPlayers(roomId, players);
  }

  // 스킵 투표 알림 수신
  @MessageMapping("/room/{roomId}/skip")
  public void handleSkipVote(@DestinationVariable String roomId, ChatMessage message) {
    gameService.handleSkipVote(roomId, message.getSender(), messagingTemplate);
  }
}