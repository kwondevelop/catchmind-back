package com.catchmindback.controller;

import com.catchmindback.entity.GameRoom;
import com.catchmindback.entity.Player;
import com.catchmindback.service.GameService; // 👈 추가된 import
import com.catchmindback.service.LobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lobby")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 프론트엔드(Vue) 포트 접근 허용
public class LobbyController {

  private final LobbyService lobbyService;
  private final GameService gameService; // 👈 방 인원수 조회를 위해 GameService 주입 추가

  // 1. 닉네임 등록 API (POST /api/lobby/player)
  @PostMapping("/player")
  public ResponseEntity<Player> registerPlayer(@RequestBody Map<String, String> request) {
    String nickname = request.get("nickname");
    Player player = lobbyService.createPlayer(nickname);
    return ResponseEntity.ok(player);
  }

  // 2. 방 생성 API (POST /api/lobby/room)
  @PostMapping("/room")
  public ResponseEntity<GameRoom> createRoom(@RequestBody Map<String, String> request) {
    String roomName = request.get("roomName");
    GameRoom newRoom = lobbyService.createRoom(roomName);
    return ResponseEntity.ok(newRoom);
  }

  // 3. 방 목록 조회 API (GET /api/lobby/rooms) - 💡 방 인원수 실시간 반영 로직 추가
  @GetMapping("/rooms")
  public ResponseEntity<List<GameRoom>> getRooms() {
    List<GameRoom> rooms = lobbyService.getAllRooms();

    // 각 방마다 현재 게임 서비스에 접속해 있는 실시간 인원수를 계산하여 주입
    for (GameRoom room : rooms) {
      int currentCount = gameService.getPlayerCount(room.getRoomId());
      room.setCurrentPlayers(currentCount); // GameRoom 엔티티에 이 세터(Setter)가 있어야 합니다!
    }

    return ResponseEntity.ok(rooms);
  }
}