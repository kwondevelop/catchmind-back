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
      "돼지", "소", "말", "닭", "오리", "독수리", "상어", "고래", "문어", "오징어", "거북이",
      "피자", "치킨", "햄버거", "떡볶이", "라면", "김밥", "초밥", "삼겹살", "아이스크림", "케이크",
      "사과", "바나나", "포도", "수박", "딸기", "복숭아", "파인애플", "도넛", "핫도그", "감자튀김",
      "자전거", "자동차", "비행기", "우산", "시계", "안경", "모자", "신발", "가방", "냉장고",
      "텔레비전", "컴퓨터", "스마트폰", "마우스", "키보드", "칫솔", "치약", "휴지", "거울", "침대",
      "바다", "산", "하늘", "구름", "무지개", "달", "별", "번개", "나무", "꽃",
      "학교", "병원", "경찰서", "소방서", "공원", "놀이공원", "동물원", "영화관", "우주", "화장실",
      "경찰", "소방관", "의사", "요리사", "선생님", "학생", "가수", "화가", "축구", "농구",
      "수영", "낚시", "피아노", "기타", "태권도", "마술사", "도둑", "천사", "악마", "외계인"
  );

  private final Map<String, String> roomAnswers = new ConcurrentHashMap<>();
  private final Map<String, ScheduledFuture<?>> timerTasks = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
  private final Random random = new Random();

  private final Map<String, List<RoomPlayer>> roomPlayers = new ConcurrentHashMap<>();
  private final Map<String, String> currentDrawers = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> roomSkipVotes = new ConcurrentHashMap<>();
  private final Map<String, List<String>> roomUsedWords = new ConcurrentHashMap<>();

  public String getCurrentDrawer(String roomId) {
    return currentDrawers.get(roomId);
  }

  // 👇 추가: 현재 방의 정답을 안전하게 가져오는 메서드
  public String getCurrentAnswer(String roomId) {
    return roomAnswers.get(roomId);
  }

  // 👇 추가: 방 인원수를 로비 API 등에서 조회할 수 있도록 제공하는 메서드
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

  public void startNewGame(String roomId, SimpMessagingTemplate messagingTemplate) {
    List<RoomPlayer> players = roomPlayers.get(roomId);
    if (players == null || players.isEmpty()) return;

    roomUsedWords.remove(roomId);
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

    ChatMessage startMsg = new ChatMessage();
    startMsg.setType("START");
    startMsg.setSender("시스템");
    startMsg.setMessage(newWord);
    startMsg.setDrawerId(drawerId);
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", startMsg);

    final int[] timeLeft = {60};
    ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
      if (timeLeft[0] >= 0) {
        ChatMessage timeMsg = new ChatMessage();
        timeMsg.setType("TIME");
        timeMsg.setSender("시스템");
        timeMsg.setMessage(String.valueOf(timeLeft[0]));
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", timeMsg);
        timeLeft[0]--;
      } else {
        stopTimer(roomId);
        roomAnswers.remove(roomId);

        ChatMessage timeOutMsg = new ChatMessage();
        timeOutMsg.setType("SYSTEM");
        timeOutMsg.setSender("시스템");
        timeOutMsg.setMessage("시간 초과! 정답은 [" + newWord + "] 이었습니다. (3초 뒤 다음 라운드)");
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", timeOutMsg);

        scheduleNextRound(roomId, messagingTemplate, 3);
      }
    }, 0, 1, TimeUnit.SECONDS);

    timerTasks.put(roomId, future);
  }

  public void handleSkipVote(String roomId, String nickname, SimpMessagingTemplate messagingTemplate) {
    List<RoomPlayer> players = roomPlayers.get(roomId);
    if (players == null || players.size() < 2) return;

    roomSkipVotes.putIfAbsent(roomId, Collections.synchronizedSet(new HashSet<>()));
    Set<String> votes = roomSkipVotes.get(roomId);
    votes.add(nickname);

    int totalPlayers = players.size();
    int voters = totalPlayers - 1;
    int requiredVotes = (voters / 2) + 1;

    ChatMessage voteMsg = new ChatMessage();
    voteMsg.setType("SYSTEM");
    voteMsg.setSender("시스템");
    voteMsg.setMessage(nickname + "님이 스킵 투표를 했습니다. (" + votes.size() + "/" + requiredVotes + ")");
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", voteMsg);

    if (votes.size() >= requiredVotes) {
      stopTimer(roomId);
      String currentAnswer = roomAnswers.remove(roomId);

      ChatMessage skipMsg = new ChatMessage();
      skipMsg.setType("SYSTEM");
      skipMsg.setSender("시스템");
      skipMsg.setMessage("과반수 투표로 라운드가 스킵되었습니다! 정답은 [" + currentAnswer + "] (3초 뒤 다음 라운드)");
      messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", skipMsg);

      clearRoomCanvas(roomId, messagingTemplate);
      scheduleNextRound(roomId, messagingTemplate, 3);
    }
  }

  public boolean isCorrectAnswer(String roomId, String chatMessage) {
    String currentAnswer = roomAnswers.get(roomId);
    if (currentAnswer != null && currentAnswer.equals(chatMessage.trim())) {
      // 💡 참고: 정답을 맞춘 즉시 roomAnswers에서 remove하면 정답 단어가 사라지므로,
      // GameController나 정답 처리 로직에서 remove하기 전에 미리 getCurrentAnswer()로 단어를 빼내야 합니다!
      return true;
    }
    return false;
  }

  // 정답을 맞췄을 때 안전하게 정답을 지우고 반환하는 메서드 추가
  public String processCorrectAnswer(String roomId) {
    return roomAnswers.remove(roomId);
  }

  public void scheduleNextRound(String roomId, SimpMessagingTemplate messagingTemplate, int delaySeconds) {
    scheduler.schedule(() -> {
      startNextRound(roomId, messagingTemplate);
    }, delaySeconds, TimeUnit.SECONDS);
  }

  public void stopTimer(String roomId) {
    ScheduledFuture<?> future = timerTasks.get(roomId);
    if (future != null && !future.isDone()) {
      future.cancel(true);
    }
  }

  public void clearRoomCanvas(String roomId, SimpMessagingTemplate messagingTemplate) {
    Map<String, String> clearMap = Map.of("type", "CLEAR");
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/draw", clearMap);
  }

  public List<RoomPlayer> playerEnter(String roomId, String nickname) {
    roomPlayers.putIfAbsent(roomId, Collections.synchronizedList(new ArrayList<>()));
    List<RoomPlayer> players = roomPlayers.get(roomId);

    synchronized (players) {
      boolean exists = players.stream().anyMatch(p -> p.getNickname().equals(nickname));
      if (!exists) {
        boolean isHost = players.isEmpty();
        players.add(new RoomPlayer(nickname, false, isHost));
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

  public List<RoomPlayer> playerLeave(String roomId, String nickname) {
    List<RoomPlayer> players = roomPlayers.get(roomId);
    if (players != null) {
      synchronized (players) {
        players.removeIf(p -> p.getNickname().equals(nickname));

        if (!players.isEmpty() && players.stream().noneMatch(RoomPlayer::isHost)) {
          players.get(0).setHost(true);
        }
      }
    }
    return players;
  }
}