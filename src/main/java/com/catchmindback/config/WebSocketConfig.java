package com.catchmindback.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // 프론트엔드(Vue)에서 연결할 SockJS 엔드포인트 설정
    registry.addEndpoint("/ws-game")
        .setAllowedOriginPatterns("*") // CORS 허용 (Vue 로컬 포트 접속 허용)
        .withSockJS();
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    // 서버 -> 클라이언트로 브로드캐스팅 할 때 사용할 Prefix
    registry.enableSimpleBroker("/topic");

    // 클라이언트 -> 서버로 메시지를 보낼 때 매핑될 Prefix
    registry.setApplicationDestinationPrefixes("/app");
  }
}