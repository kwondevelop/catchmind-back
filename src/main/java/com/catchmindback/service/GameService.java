package com.catchmindback.service;

import com.catchmindback.dto.ChatMessage;
import com.catchmindback.dto.RoomPlayer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class GameService {

  private final List<String> wordList = Arrays.asList(
      "강아지", "고양이", "호랑이", "사자", "코끼리", "기린", "원숭이", "토끼", "판다", "펭귄",
      "피자", "치킨", "햄버거", "떡볶이", "라면", "김밥", "초밥", "삼겹살", "아이스크림", "케이크",
      "자전거", "자동차", "비행기", "우산", "시계", "안경", "모자", "신발", "가방", "냉장고",
      "바다", "산", "하늘", "구름", "무지개", "달", "별", "번개", "나무", "꽃",
      "경찰", "소방관", "의사", "요리사", "선생님", "학생", "가수", "화가", "축구", "농구"
  );

  private final Map<String, String> roomAnswers = new ConcurrentHashMap<>();
  private final Map<String, ScheduledFuture<?>> timerTasks = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
  private final Random random = new Random();

  private final Map<String, List<RoomPlayer>> roomPlayers = new ConcurrentHashMap<>();
  private final Map<String, String> currentDrawers = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> roomSkipVotes = new ConcurrentHashMap<>();
  private final Map<String, List<String>> roomUsedWords = new ConcurrentHashMap<>();

  private final Map<String, Boolean> roomPlayingStatus = new ConcurrentHashMap<>();
  private final Map<String, Integer> roomCurrentRound = new ConcurrentHashMap<>();
  private final Map<String, Integer> roomMaxRound = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Integer>> roomScores = new ConcurrentHashMap<>();

  public String getCurrentDrawer(String roomId) {
    return currentDrawers.get(roomId);
  }

  public int getPlayerCount(String roomId) {
    List<RoomPlayer> players = roomPlayers.get(roomId);
    return players == null ? 0 : players.size();
  }

  private String pickNextWord(String roomId) {
    roomUsedWords.putIfAbsent(roomId, Collections.synchronizedList(new ArrayList<>()));
    List<String> used = roomUsedWords.get(roomId);

    synchronized (used) {
      List<String> available = new ArrayList<>(wordList);
      available.removeAll(used);
      if (available.isEmpty()) {
        used.clear();
        available.addAll(wordList);
      }
      String nextWord = available.get(random.nextInt(available.size()));
      used.add(nextWord);
      return nextWord;
    }
  }

  // 편의 메서드: 변경된 유저 명단(점수 포함)을 방 전체에 브로드캐스트
  private void broadcastPlayers(String roomId, List<RoomPlayer> players, SimpMessagingTemplate messagingTemplate) {
    ChatMessage msg = new ChatMessage();
    msg.setType("PLAYERS");
    msg.setSender("시스템");
    msg.setData(players);
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", msg);
  }

  // 파라미터에 int maxRound를 추가하고 5 대신 사용합니다.
  public void startNewGame(String roomId, int maxRound, SimpMessagingTemplate messagingTemplate) {
    List<RoomPlayer> players = roomPlayers.get(roomId);
    if (players == null || players.isEmpty()) return;

    roomPlayingStatus.put(roomId, true);
    roomCurrentRound.put(roomId, 1);

    // 고정값 5 대신 방 설정에서 가져온 라운드 수 적용
    roomMaxRound.put(roomId, maxRound);

    Map<String, Integer> scores = new ConcurrentHashMap<>();
    synchronized (players) {
      for (RoomPlayer p : players) {
        scores.put(p.getNickname(), 0);
        p.setScore(0);
      }
    }
    roomScores.put(roomId, scores);
    roomUsedWords.remove(roomId);

    broadcastPlayers(roomId, players, messagingTemplate);

    int firstIndex = random.nextInt(players.size());
    String firstDrawer = players.get(firstIndex).getNickname();
    currentDrawers.put(roomId, firstDrawer);

    startRound(roomId, firstDrawer, messagingTemplate);
  }

  public void startNextRound(String roomId, SimpMessagingTemplate messagingTemplate) {
    List<RoomPlayer> players = roomPlayers.get(roomId);
    if (players == null || players.isEmpty()) return;

    String currentDrawer = currentDrawers.get(roomId);
    int nextIndex = 0;
    if (currentDrawer != null) {
      synchronized (players) {
        for (int i = 0; i < players.size(); i++) {
          if (players.get(i).getNickname().equals(currentDrawer)) {
            nextIndex = (i + 1) % players.size();
            break;
          }
        }
      }
    }
    String nextDrawer = players.get(nextIndex).getNickname();
    currentDrawers.put(roomId, nextDrawer);
    startRound(roomId, nextDrawer, messagingTemplate);
  }

  private void startRound(String roomId, String drawerId, SimpMessagingTemplate messagingTemplate) {
    stopTimer(roomId);
    roomSkipVotes.remove(roomId);

    String newWord = pickNextWord(roomId);
    roomAnswers.put(roomId, newWord);
    clearRoomCanvas(roomId, messagingTemplate);

    // 현재 라운드와 최대 라운드 가져오기
    int currentRound = roomCurrentRound.getOrDefault(roomId, 1);
    int maxRound = roomMaxRound.getOrDefault(roomId, 10);

    ChatMessage startMsg = new ChatMessage();
    startMsg.setType("START");
    startMsg.setSender("시스템");
    startMsg.setMessage(newWord);
    startMsg.setDrawerId(drawerId);

    startMsg.setCurrentRound(currentRound);
    startMsg.setMaxRound(maxRound);

    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", startMsg);

    final int[] timeLeft = {180};
    ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
      if (timeLeft[0] >= 0) {
        ChatMessage timeMsg = new ChatMessage();
        timeMsg.setType("TIME");
        timeMsg.setSender("시스템");
        timeMsg.setMessage(String.valueOf(timeLeft[0]));
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", timeMsg);
        timeLeft[0]--;
      } else {
        endRound(roomId, "TIMEOUT", null, messagingTemplate);
      }
    }, 0, 1, TimeUnit.SECONDS);
    timerTasks.put(roomId, future);
  }

  public void endRound(String roomId, String reason, String winnerNickname, SimpMessagingTemplate messagingTemplate) {
    stopTimer(roomId);
    String answer = roomAnswers.remove(roomId);
    clearRoomCanvas(roomId, messagingTemplate);

    int currentRound = roomCurrentRound.getOrDefault(roomId, 1);
    int maxRound = roomMaxRound.getOrDefault(roomId, 5);

    // 정답일 경우 점수 부여 및 프론트엔드 명단(점수) 실시간 업데이트
    if ("CORRECT".equals(reason) && winnerNickname != null) {
      roomScores.putIfAbsent(roomId, new ConcurrentHashMap<>());
      Map<String, Integer> scores = roomScores.get(roomId);
      int newScore = scores.getOrDefault(winnerNickname, 0) + 10;
      scores.put(winnerNickname, newScore);

      List<RoomPlayer> players = roomPlayers.get(roomId);
      if (players != null) {
        synchronized (players) {
          for (RoomPlayer p : players) {
            if (p.getNickname().equals(winnerNickname)) {
              p.setScore(newScore); // RoomPlayer 객체 점수 갱신
              break;
            }
          }
        }
        broadcastPlayers(roomId, players, messagingTemplate); // 갱신된 점수를 화면에 전송!
      }
    }

    ChatMessage systemMsg = new ChatMessage();
    systemMsg.setType("SYSTEM");
    systemMsg.setSender("시스템");

    if ("CORRECT".equals(reason)) {
      systemMsg.setMessage(winnerNickname + "님이 정답을 맞췄습니다! \n정답: [" + answer + "] \n(" + currentRound + "/" + maxRound + " 라운드 종료)");
    } else if ("SKIP".equals(reason)) {
      systemMsg.setMessage("과반수 투표로 라운드 스킵! \n정답: [" + answer + "] \n(" + currentRound + " 라운드 종료)");
    } else if ("TIMEOUT".equals(reason)) {
      systemMsg.setMessage("시간 초과! \n정답: [" + answer + "] \n(" + currentRound + " 라운드 종료)");
    }
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", systemMsg);

    if (currentRound >= maxRound) {
      scheduleGameEnd(roomId, messagingTemplate, 3);
    } else {
      roomCurrentRound.put(roomId, currentRound + 1);
      scheduleNextRound(roomId, messagingTemplate, 3);
    }
  }

  public void handleSkipVote(String roomId, String nickname, SimpMessagingTemplate messagingTemplate) {
    List<RoomPlayer> players = roomPlayers.get(roomId);
    if (players == null || players.size() < 2) return;

    roomSkipVotes.putIfAbsent(roomId, Collections.synchronizedSet(new HashSet<>()));
    Set<String> votes = roomSkipVotes.get(roomId);
    votes.add(nickname);

    int requiredVotes = ((players.size() - 1) / 2) + 1;
    ChatMessage voteMsg = new ChatMessage();
    voteMsg.setType("SYSTEM");
    voteMsg.setSender("시스템");
    voteMsg.setMessage(nickname + "님이 스킵 투표를 했습니다. (" + votes.size() + "/" + requiredVotes + ")");
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", voteMsg);

    if (votes.size() >= requiredVotes) {
      endRound(roomId, "SKIP", null, messagingTemplate);
    }
  }

  private void scheduleGameEnd(String roomId, SimpMessagingTemplate messagingTemplate, int delaySeconds) {
    scheduler.schedule(() -> {
      Map<String, Integer> scores = roomScores.getOrDefault(roomId, new HashMap<>());
      String winner = "없음";
      int maxScore = -1;
      for (Map.Entry<String, Integer> entry : scores.entrySet()) {
        if (entry.getValue() > maxScore) {
          maxScore = entry.getValue();
          winner = entry.getKey();
        }
      }

      ChatMessage endMsg = new ChatMessage();
      endMsg.setType("SYSTEM");
      endMsg.setSender("시스템");
      endMsg.setMessage("게임 종료 \n최종 우승자: [" + winner + "] (" + Math.max(maxScore, 0) + "점)\n모든 플레이어의 준비 상태가 초기화됩니다.");
      messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", endMsg);

      roomPlayingStatus.put(roomId, false);
      roomCurrentRound.remove(roomId);
      roomScores.remove(roomId);
      currentDrawers.remove(roomId);
      roomAnswers.remove(roomId);

      List<RoomPlayer> players = roomPlayers.get(roomId);
      if (players != null) {
        synchronized (players) {
          for (RoomPlayer p : players) {
            p.setReady(false);
            p.setScore(0); // 다음 게임을 위해 점수 초기화
          }
        }
        broadcastPlayers(roomId, players, messagingTemplate); // 갱신된 내역(준비 취소, 점수 초기화) 전송
      }
    }, delaySeconds, TimeUnit.SECONDS);
  }

  public boolean isCorrectAnswer(String roomId, String chatMessage) {
    String currentAnswer = roomAnswers.get(roomId);
    return currentAnswer != null && currentAnswer.equals(chatMessage.trim());
  }

  public String processCorrectAnswer(String roomId) {
    return roomAnswers.remove(roomId);
  }

  public void scheduleNextRound(String roomId, SimpMessagingTemplate messagingTemplate, int delaySeconds) {
    scheduler.schedule(() -> { startNextRound(roomId, messagingTemplate); }, delaySeconds, TimeUnit.SECONDS);
  }

  public void stopTimer(String roomId) {
    ScheduledFuture<?> future = timerTasks.get(roomId);
    if (future != null && !future.isDone()) future.cancel(true);
  }

  public void clearRoomCanvas(String roomId, SimpMessagingTemplate messagingTemplate) {
    Map<String, String> clearMap = Map.of("type", "CLEAR");
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/draw", clearMap);
  }

  public List<RoomPlayer> playerEnter(String roomId, String nickname) {
    if (roomPlayingStatus.getOrDefault(roomId, false)) {
      throw new IllegalStateException("이미 게임이 진행 중인 방입니다.");
    }
    roomPlayers.putIfAbsent(roomId, Collections.synchronizedList(new ArrayList<>()));
    List<RoomPlayer> players = roomPlayers.get(roomId);
    synchronized (players) {
      boolean exists = players.stream().anyMatch(p -> p.getNickname().equals(nickname));
      if (!exists) {
        players.add(new RoomPlayer(nickname, false, players.isEmpty())); // 점수는 RoomPlayer 객체 기본값 0으로 들어감
      }
    }
    return players;
  }

  public List<RoomPlayer> toggleReady(String roomId, String nickname) {
    List<RoomPlayer> players = roomPlayers.get(roomId);
    if (players != null) {
      synchronized (players) {
        for (RoomPlayer p : players) {
          if (p.getNickname().equals(nickname)) {
            p.setReady(!p.isReady());
            break;
          }
        }
      }
    }
    return players;
  }

  public List<RoomPlayer> playerLeave(String roomId, String nickname, SimpMessagingTemplate messagingTemplate) {
    List<RoomPlayer> players = roomPlayers.get(roomId);
    if (players != null) {
      synchronized (players) {
        players.removeIf(p -> p.getNickname().equals(nickname));
        if (!players.isEmpty() && players.stream().noneMatch(RoomPlayer::isHost)) {
          players.get(0).setHost(true);
        }
      }

      // 게임이 진행 중인 상태에서 남은 인원이 2명 미만이 되면 강제 종료
      if (roomPlayingStatus.getOrDefault(roomId, false) && players.size() < 2) {
        stopTimer(roomId);
        roomPlayingStatus.put(roomId, false);
        roomCurrentRound.remove(roomId);
        roomScores.remove(roomId);
        currentDrawers.remove(roomId);
        roomAnswers.remove(roomId);

        // 남은 플레이어들의 준비 상태 초기화
        for (RoomPlayer p : players) {
          p.setReady(false);
          p.setScore(0);
        }

        // 강제 종료 안내 시스템 메시지 전송
        ChatMessage abortMsg = new ChatMessage();
        abortMsg.setType("SYSTEM");
        abortMsg.setSender("시스템");
        abortMsg.setMessage("⚠️ 플레이어 이탈로 인해 게임이 강제 종료되었습니다. \n(최소 인원 부족)");
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", abortMsg);

        // 갱신된 플레이어 명단 방송
        ChatMessage playerMsg = new ChatMessage();
        playerMsg.setType("PLAYERS");
        playerMsg.setSender("시스템");
        playerMsg.setData(players);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", playerMsg);
      }
    }
    return players;
  }

  // 방이 텅 비었을 때 서버 메모리를 깔끔하게 정리하는 메서드
  public void cleanupRoom(String roomId) {
    stopTimer(roomId); // 혹시 돌아가고 있는 타이머가 있다면 정지
    roomPlayers.remove(roomId);
    currentDrawers.remove(roomId);
    roomSkipVotes.remove(roomId);
    roomUsedWords.remove(roomId);
    roomPlayingStatus.remove(roomId);
    roomCurrentRound.remove(roomId);
    roomMaxRound.remove(roomId);
    roomScores.remove(roomId);
    roomAnswers.remove(roomId);
  }
}