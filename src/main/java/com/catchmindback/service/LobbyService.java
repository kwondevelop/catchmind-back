package com.catchmindback.service;

import com.catchmindback.entity.GameRoom;
import com.catchmindback.entity.Player;
import com.catchmindback.repository.GameRoomRepository;
import com.catchmindback.repository.PlayerRepository;
import jakarta.annotation.PostConstruct; // 💡 추가됨: 서버 시작 시 실행을 위한 어노테이션
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LobbyService {

  private final PlayerRepository playerRepository;
  private final GameRoomRepository gameRoomRepository;

  // 서버가 켜질 때 기존에 남아있던 방 데이터를 모두 삭제하여 초기화
  @PostConstruct
  @Transactional
  public void initLobby() {
    gameRoomRepository.deleteAll();
    playerRepository.deleteAll();
    System.out.println("== [서버 시작] 데이터가 모두 초기화되었습니다. ==");
  }

  // 유저 닉네임 생성 (중복 시 기존 유저 반환)
  @Transactional
  public Player createPlayer(String nickname) {
    return playerRepository.findByNickname(nickname)
        .orElseGet(() -> {
          Player newPlayer = new Player();
          newPlayer.setNickname(nickname);
          return playerRepository.save(newPlayer);
        });
  }

  // 방 생성 (UUID로 고유 방 번호 부여)
  @Transactional
  public GameRoom createRoom(String roomName, int maxPlayers, int maxRound) {
    GameRoom room = new GameRoom();
    room.setRoomId("room-" + UUID.randomUUID().toString().substring(0, 8));
    room.setRoomName(roomName);
    room.setMaxPlayers(maxPlayers);
    room.setMaxRound(maxRound);
    room.setCurrentPlayers(0);
    room.setPlaying(false);

    return gameRoomRepository.save(room);
  }

  // 전체 방 목록 조회
  @Transactional(readOnly = true)
  public List<GameRoom> getAllRooms() {
    return gameRoomRepository.findAll();
  }

  // 게임 상태 변경 (난입 방지 및 로비 표시용)
  @Transactional
  public void updateRoomPlayingStatus(String roomId, boolean isPlaying) {
    gameRoomRepository.findById(roomId).ifPresent(room -> {
      room.setPlaying(isPlaying);
      gameRoomRepository.save(room);
    });
  }

  // 방 삭제 기능 추가 (인원이 0명이 되었을 때 호출됨)
  @Transactional
  public void deleteRoom(String roomId) {
    if (gameRoomRepository.existsById(roomId)) {
      gameRoomRepository.deleteById(roomId);
    }
  }

  // 방장이 게임을 시작할 때 설정된 라운드 수를 조회하기 위한 메서드
  @Transactional(readOnly = true)
  public int getRoomMaxRound(String roomId) {
    return gameRoomRepository.findById(roomId)
        .map(GameRoom::getMaxRound)
        .orElse(5); // 기본값
  }
}