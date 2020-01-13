package com.barexas.game.communicator.controller;

import com.barexas.game.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    @MessageMapping("/input")
    public void processMessageFromClient(@Payload String payload, @Header("simpSessionId") String sessionId) throws Exception {
        log.info("Socket [{}] send [{}]",sessionId, payload);
        kafkaTemplate.send("events", new Event("INPUT", sessionId, payload));
    }
}