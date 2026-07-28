package com.catchmindback.service;

import com.catchmindback.entity.GameRoom;
import com.catchmindback.entity.Player;
import com.catchmindback.repository.GameRoomRepository;
import com.catchmindback
    .repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LobbyService {

  private final PlayerRepository playerRepository;
  private final GameRoomRepository gameRoomRepository;

  // 1. 유저 닉네임 생성 (중복 시 기존 유저 반환 또는 에러 처리 가능)
  public Player createPlayer(String nickname) {
    return playerRepository.findByNickname(nickname)
        .orElseGet(() -> {
          Player newPlayer = new Player();
          newPlayer.setNickname(nickname);
          return playerRepository.save(newPlayer);
        });
  }

  // 2. 방 생성 (UUID로 고유 방 번호 부여)
  public GameRoom createRoom(String roomName) {
    GameRoom room = new GameRoom();
    room.setRoomId("room-" + UUID.randomUUID().toString().substring(0, 8)); // 짧은 UUID
    room.setRoomName(roomName);
    room.setMaxPlayers(8);
    room.setCurrentPlayers(0);
    room.setPlaying(false);

    return gameRoomRepository.save(room);
  }

  // 3. 전체 방 목록 조회
  public List<GameRoom> getAllRooms() {
    return gameRoomRepository.findAll();
  }
}