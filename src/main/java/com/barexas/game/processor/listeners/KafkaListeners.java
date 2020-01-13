package com.barexas.game.processor.listeners;

import com.barexas.game.model.*;
import com.barexas.game.processor.service.LadderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaListeners {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper mapper;
    private final LadderService ladderService;

    @EventListener
    public void notifyLadder(LadderUpdatedEvent event){
        kafkaTemplate.send("notify", Map.of("type","LADDER","ladder", ladderService.getLadder()));
    }

    @EventListener
    public void notifyPrivate(SendPrivate event){
        List<Object> list = (List<Object>) event.getSource();
        list.forEach(item -> {
            kafkaTemplate.send("output", item);
        });
    }

    @KafkaListener(topics = "events", groupId = "1")
    public void listener(String event) throws Exception {
        try {
            log.info("Received new event > {}", event);
            Event pojo = mapper.readValue(event, Event.class);
            switch (pojo.getType()) {
                case "CONNECTED": {
                    List<Object> buffer = ladderService.setOnline(String.valueOf(pojo.getData()), pojo.getSessionId());
                    Integer score = ladderService.getTargetScore(String.valueOf(pojo.getData()));
                    kafkaTemplate.send("output", Map.of("target", pojo.getData(),
                            "data", Map.of("type","SCORE","score", score)));
                    kafkaTemplate.send("notify", Map.of("type","ONLINE_LIST","data", ladderService.getOnline()));

                    buffer.forEach(item -> {
                        kafkaTemplate.send("output", Map.of("target", pojo.getData(),
                                "data", item));
                    });
                    break;
                }
                case "DISCONNECTED":{
                    ladderService.setOffline(String.valueOf(pojo.getSessionId()));
                    kafkaTemplate.send("notify", Map.of("type","ONLINE_LIST","data", ladderService.getOnline()));
                    break;
                }
                case "INPUT":{
                    processUserInput(pojo);
                    break;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void processUserInput(Event pojo) throws Exception {
        Map<String, Object> data = mapper.readValue((String) pojo.getData(), HashMap.class);
        switch (String.valueOf(data.get("type"))){
            case "CHALLENGE":{
                String targetId = String.valueOf(data.get("user"));
                String sourceSessionId = pojo.getSessionId();
                List<Object> list = ladderService.challenge(sourceSessionId, targetId);
                list.forEach(item -> {
                    kafkaTemplate.send("output", item);
                });
                break;
            }
            case "DECLINE_BATTLE":{
                Long matchId = Long.valueOf(String.valueOf(data.get("id")));
                List<Object> list = ladderService.decline(matchId);
                list.forEach(item -> {
                    kafkaTemplate.send("output", item);
                });
                break;
            }
            case "ACCEPT_BATTLE":{
                Long matchId = Long.valueOf(String.valueOf(data.get("id")));
                List<Object> list = ladderService.accept(matchId, pojo.getSessionId());
                list.forEach(item -> {
                    kafkaTemplate.send("output", item);
                });
                break;
            }
            case "START_MM":{
                ladderService.startMM(pojo.getSessionId());
                break;
            }
            case "STOP_MM":{
                ladderService.stopMM(pojo.getSessionId());
                break;
            }
        }
    }
}
