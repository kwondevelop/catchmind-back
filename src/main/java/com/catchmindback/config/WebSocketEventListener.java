package com.catchmindback.config;

import com.catchmindback.dto.ChatMessage;
import com.catchmindback.dto.RoomPlayer;
import com.catchmindback.service.GameService;
import com.catchmindback.service.LobbyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

  public static final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();
  public static final Map<String, String> sessionRoomMap = new ConcurrentHashMap<>();

  private final GameService gameService;
  private final LobbyService lobbyService;
  private final SimpMessagingTemplate messagingTemplate;

  // 인터넷이 끊기거나 브라우저를 강제 종료했을 때 자동으로 실행
  @EventListener
  public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    String sessionId = headerAccessor.getSessionId();

    // 1. 끊긴 세션 ID로 유저가 어느 방에 있었는지 찾음
    String nickname = sessionUserMap.get(sessionId);
    String roomId = sessionRoomMap.get(sessionId);

    if (nickname != null && roomId != null) {
      log.info("== [연결 끊김 감지] 유저: {}, 방: {} ==", nickname, roomId);

      // 2. 강제 퇴장 처리 (GameController의 handleLeave와 동일한 로직)
      List<RoomPlayer> players = gameService.playerLeave(roomId, nickname, messagingTemplate);

      // 3. 인원이 0명이 되면 텅 빈 좀비 방을 완전히 삭제
      if (players != null && players.isEmpty()) {
        gameService.cleanupRoom(roomId);
        lobbyService.deleteRoom(roomId);
        log.info("== [방 삭제] 방 {} 의 인원이 0명이 되어 삭제되었습니다. ==", roomId);
      } else if (players != null) {
        // 방에 사람이 남아있다면 갱신된 플레이어 목록 전송
        ChatMessage msg = new ChatMessage();
        msg.setType("PLAYERS");
        msg.setSender("시스템");
        msg.setData(players);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", msg);
      }

      // 4. 메모리 정리
      sessionUserMap.remove(sessionId);
      sessionRoomMap.remove(sessionId);
    }
  }
}