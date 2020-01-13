package com.barexas.game.communicator.listeners;

import com.barexas.game.model.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketEventListener {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SimpMessageSendingOperations messagingTemplate;
    private final ObjectMapper mapper;

    @EventListener
    public void connectEvent(SessionConnectedEvent event) {
        Map<String, Object> nativeHeaders = (Map<String, Object>) ((GenericMessage) event.getMessage().getHeaders().get("simpConnectMessage")).getHeaders().get("nativeHeaders");
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        if (nativeHeaders.containsKey("test")) {
            String id = ((List<String>) nativeHeaders.get("test")).get(0);
            kafkaTemplate.send("events", new Event("CONNECTED", sessionId, id));
        }
    }

    @EventListener
    public void disconnectEvent(SessionDisconnectEvent event) {
        kafkaTemplate.send("events", new Event("DISCONNECTED", event.getSessionId(), ""));
    }

    @KafkaListener(topics = "output", groupId = "1")
    public void listener(String event) throws Exception {
        try {
            Map<String, Object> temp = mapper.readValue(event, Map.class);
            log.info("Sending event > {} > to privates", event);
            messagingTemplate.convertAndSend("/private/" + temp.get("target"), temp.get("data"));
        } catch (Exception ex) {

        }
    }

    @KafkaListener(topics = "notify", groupId = "1")
    public void notifyListener(String event) throws Exception {
        try {
            log.info("Sending NOTIFY event > {}", event);
            messagingTemplate.convertAndSend("/feed/output", event);
        } catch (Exception ex) {

        }
    }
}
