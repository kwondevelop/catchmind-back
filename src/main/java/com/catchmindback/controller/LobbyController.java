package com.catchmindback.controller;

import com.catchmindback.entity.GameRoom;
import com.catchmindback.entity.Player;
import com.catchmindback.service.GameService;
import com.catchmindback.service.LobbyService;
import lombok.Data; // 💡 DTO 사용을 위해 추가
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
  private final GameService gameService; // 방 인원수 조회를 위해 GameService 주입 추가

  // 1. 닉네임 등록 API (POST /api/lobby/player)
  @PostMapping("/player")
  public ResponseEntity<Player> registerPlayer(@RequestBody Map<String, String> request) {
    String nickname = request.get("nickname");
    Player player = lobbyService.createPlayer(nickname);
    return ResponseEntity.ok(player);
  }

  // 2. 방 생성 API (POST /api/lobby/room)
  // 프론트엔드 모달에서 보내는 제목, 최대인원, 라운드수를 받기 위해 DTO(RoomCreateRequest) 사용
  @PostMapping("/room")
  public ResponseEntity<GameRoom> createRoom(@RequestBody RoomCreateRequest request) {
    GameRoom newRoom = lobbyService.createRoom(
        request.getRoomName(),
        request.getMaxPlayers(),
        request.getMaxRound()
    );
    return ResponseEntity.ok(newRoom);
  }

  // 방 목록 조회 API (GET /api/lobby/rooms) - 방 인원수 실시간 반영 로직 추가
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

  // 프론트엔드의 방 생성 데이터를 통째로 매핑받기 위한 클래스
  @Data
  public static class RoomCreateRequest {
    private String roomName;
    private int maxPlayers = 8;
    private int maxRound = 5;
  }
}