package com.catchmindback.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class WordService {

  @Getter
  private final List<String> wordList = new ArrayList<>();

  // 서버가 실행될 때(@PostConstruct) 자동으로 딱 한 번 실행되는 메서드
  @PostConstruct
  public void init() {
    try {
      // resources 폴더 안의 words.txt 파일을 찾음
      ClassPathResource resource = new ClassPathResource("words.txt");
      BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));

      String line;
      while ((line = reader.readLine()) != null) {
        String word = line.trim();
        // 빈 줄이 아니면 단어 리스트에 추가
        if (!word.isEmpty()) {
          wordList.add(word);
        }
      }
      log.info("== 단어장 로드 완료: 총 {}개의 제시어가 준비되었습니다. ==", wordList.size());

    } catch (Exception e) {
      log.error("단어장 파일을 읽어오는 중 오류가 발생했습니다.", e);
      // 만약 파일 읽기에 실패하면 비상용 단어 몇 개만 기본으로 세팅
      wordList.addAll(List.of("강아지", "고양이", "호랑이", "사과", "바나나"));
    }
  }
}