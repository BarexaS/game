package com.barexas.game.processor.service;

import com.barexas.game.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableScheduling
public class LadderService {

    private final ObjectMapper mapper;
    private final ApplicationEventPublisher publisher;
    @Getter
    private Map<String, Integer> ladder = new HashMap<>();
    private MultiValueMap<String, Object> buffers = new LinkedMultiValueMap<>();
    private MultiValueMap<Integer, String> readyForMMByScore = new LinkedMultiValueMap<>();
    @Getter
    private Map<String, String> onlineByID = new HashMap<>();
    private Map<Long, MatchDetails> matches = new HashMap<>();

    public Integer getTargetScore(String id) {
        ladder.putIfAbsent(id, 1000);
        return ladder.get(id);
    }

    public Object getOnline() {
        return onlineByID.keySet();
    }

    public List<Object> setOnline(String id, String sessionId) {
        onlineByID.put(id, sessionId);
        List<Object> result = buffers.getOrDefault(id, new LinkedList<>());
        result.add(Map.of("type", "LADDER", "ladder", getLadder()));
        return result;
    }

    public void setOffline(String sessionId) {
        String sourceId = onlineByID.entrySet().stream()
                .filter(entry -> entry.getValue().equals(sessionId))
                .map(Map.Entry::getKey)
                .findFirst().get();

        onlineByID = onlineByID.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(sessionId))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Integer score = getTargetScore(sourceId);
        readyForMMByScore.put(score,
                readyForMMByScore.get(score).stream().filter(item -> !item.equals(sourceId))
                        .collect(
                                Collectors.toList()));
    }

    public List<Object> challenge(String sourceSessionId, String targetId) {
        List<Object> result = new LinkedList<>();

        long matchId = System.currentTimeMillis();
        String sourceId = onlineByID.entrySet().stream()
                .filter(entry -> entry.getValue().equals(sourceSessionId))
                .map(Map.Entry::getKey)
                .findFirst().get();

        result.add(Map.of("target", sourceId,
                "data",
                Map.of("type", "BATTLE", "match", Map.of("id", matchId, "with", targetId))));

        if (onlineByID.containsKey(targetId)) {
            // Вызываемый онлайн
            result.add(Map.of("target", targetId,
                    "data",
                    Map.of("type", "BATTLE", "match", Map.of("id", matchId, "with", sourceId))));
        }
        buffers.add(targetId,
                Map.of("type", "BATTLE", "match", Map.of("id", matchId, "with", sourceId)));
        buffers.add(sourceId,
                Map.of("type", "BATTLE", "match", Map.of("id", matchId, "with", targetId)));
        matches.put(matchId, new MatchDetails(sourceId, targetId));

        return result;
    }

    public List<Object> decline(Long matchId) {
        List<Object> result = new LinkedList<>();
        if (matches.containsKey(matchId)) {
            MatchDetails matchDetails = matches.get(matchId);
            Stream.of(matchDetails.getFirstId(), matchDetails.getSecondId())
                    .forEach(targetId -> {
                        if (onlineByID.containsKey(targetId)) {
                            // Вызываемый онлайн
                            result.add(Map.of("target", targetId,
                                    "data", Map.of("type", "DECLINE_BATTLE", "match",
                                            Map.of("id", matchId))));
                        }
                        List<Object> buffer = buffers.getOrDefault(targetId, new LinkedList<>());
                        buffer = buffer.parallelStream()
                                .map(item -> mapper.convertValue(item, HashMap.class))
                                .filter(item -> {
                                    try {
                                        Map<String, Object> details = mapper
                                                .convertValue(item.get("match"), HashMap.class);
                                        String detailsMatchId = String.valueOf(details.get("id"));
                                        return !detailsMatchId.equals(matchId.toString());
                                    } catch (Exception ex) {
                                        return true;
                                    }
                                })
                                .collect(Collectors.toList());
                        buffers.put(targetId, buffer);
                    });
            matches.remove(matchId);
        }
        return result;
    }

    public List<Object> accept(Long matchId, String sessionId) {
        List<Object> result = new LinkedList<>();
        String sourceId = onlineByID.entrySet().stream()
                .filter(entry -> entry.getValue().equals(sessionId))
                .map(Map.Entry::getKey)
                .findFirst().get();
        if (matches.containsKey(matchId)) {
            MatchDetails matchDetails = matches.get(matchId);
            if (matchDetails.getFirstId().equals(sourceId) && !matchDetails.isFirstApprove()) {
                matchDetails.setFirstApprove(true);
                result.add(Map.of("target", sourceId,
                        "data", Map.of("type", "BATTLE_ACCEPTED", "match",
                                Map.of("id", matchId, "by", sourceId))));
                if (onlineByID.containsKey(matchDetails.getSecondId())) {
                    result.add(Map.of("target", matchDetails.getSecondId(),
                            "data", Map.of("type", "BATTLE_ACCEPTED", "match",
                                    Map.of("id", matchId, "by", sourceId))));
                }
                buffers.add(matchDetails.getFirstId(), Map.of("type", "BATTLE_ACCEPTED", "match",
                        Map.of("id", matchId, "by", sourceId)));
                buffers.add(matchDetails.getSecondId(), Map.of("type", "BATTLE_ACCEPTED", "match",
                        Map.of("id", matchId, "by", sourceId)));
            } else if (matchDetails.getSecondId().equals(sourceId) && !matchDetails
                    .isSecondApprove()) {
                matchDetails.setSecondApprove(true);
                result.add(Map.of("target", sourceId,
                        "data", Map.of("type", "BATTLE_ACCEPTED", "match",
                                Map.of("id", matchId, "by", sourceId))));
                if (onlineByID.containsKey(matchDetails.getFirstId())) {
                    result.add(Map.of("target", matchDetails.getFirstId(),
                            "data", Map.of("type", "BATTLE_ACCEPTED", "match",
                                    Map.of("id", matchId, "by", sourceId))));
                }
                buffers.add(matchDetails.getFirstId(), Map.of("type", "BATTLE_ACCEPTED", "match",
                        Map.of("id", matchId, "by", sourceId)));
                buffers.add(matchDetails.getSecondId(), Map.of("type", "BATTLE_ACCEPTED", "match",
                        Map.of("id", matchId, "by", sourceId)));
            }
            matches.put(matchId, matchDetails);
            if (matchDetails.isReady()) {
                String winner = processMatch(matchDetails);

                Stream.of(matchDetails.getFirstId(), matchDetails.getSecondId())
                        .forEach(targetId -> {
                            List<Object> buffer = buffers
                                    .getOrDefault(targetId, new LinkedList<>());
                            buffer = buffer.parallelStream()
                                    .map(item -> mapper.convertValue(item, HashMap.class))
                                    .filter(item -> {
                                        try {
                                            Map<String, Object> details = mapper
                                                    .convertValue(item.get("match"), HashMap.class);
                                            String detailsMatchId = String
                                                    .valueOf(details.get("id"));
                                            return !detailsMatchId.equals(matchId.toString());
                                        } catch (Exception ex) {
                                            return true;
                                        }
                                    })
                                    .collect(Collectors.toList());
                            buffers.put(targetId, buffer);
                        });

                //Notify ladder and players
                result = new LinkedList<>();
                result.add(Map.of("target", matchDetails.getFirstId(),
                        "data", Map.of("type", "BATTLE_COMPLETE", "match",
                                Map.of("id", matchId, "winner", winner))));
                result.add(Map.of("target", matchDetails.getSecondId(),
                        "data", Map.of("type", "BATTLE_COMPLETE", "match",
                                Map.of("id", matchId, "winner", winner))));
                matches.remove(matchId);

                publisher.publishEvent(new LadderUpdatedEvent(matchId));
            }
        }
        return result;
    }

    private String processMatch(MatchDetails matchDetails) {
        if (Math.random() >= 0.5) {
            String winnerId = matchDetails.getFirstId();
            String looserId = matchDetails.getSecondId();
            Integer winnerScore = ladder.get(winnerId);
            Integer looserScore = ladder.get(looserId);

            Integer newWinnerScore = winnerScore + 25;
            Integer newLooserScore = looserScore - 25;

            if (readyForMMByScore.containsKey(winnerScore)) {
                readyForMMByScore.put(winnerScore, readyForMMByScore.get(winnerScore).stream()
                        .filter(item -> !item.equals(winnerId)).collect(
                                Collectors.toList()));
                readyForMMByScore.add(newWinnerScore, winnerId);

            }
            if (readyForMMByScore.containsKey(looserScore)) {
                readyForMMByScore.put(looserScore, readyForMMByScore.get(looserScore).stream()
                        .filter(item -> !item.equals(looserId)).collect(
                                Collectors.toList()));
                readyForMMByScore.add(newLooserScore, looserId);
            }

            ladder.put(winnerId, newWinnerScore);
            ladder.put(looserId, newLooserScore);
            return winnerId;
        } else {
            String looserId = matchDetails.getFirstId();
            String winnerId = matchDetails.getSecondId();
            Integer winnerScore = ladder.get(winnerId);
            Integer looserScore = ladder.get(looserId);

            Integer newWinnerScore = winnerScore + 25;
            Integer newLooserScore = looserScore - 25;

            if (readyForMMByScore.containsKey(winnerScore)) {
                readyForMMByScore.put(winnerScore, readyForMMByScore.get(winnerScore).stream()
                        .filter(item -> !item.equals(winnerId)).collect(
                                Collectors.toList()));
                readyForMMByScore.add(newWinnerScore, winnerId);

            }
            if (readyForMMByScore.containsKey(looserScore)) {
                readyForMMByScore.put(looserScore, readyForMMByScore.get(looserScore).stream()
                        .filter(item -> !item.equals(looserId)).collect(
                                Collectors.toList()));
                readyForMMByScore.add(newLooserScore, looserId);
            }
            ladder.put(winnerId, newWinnerScore);
            ladder.put(looserId, newLooserScore);
            return winnerId;
        }
    }

    public void startMM(String sessionId) {
        String sourceId = onlineByID.entrySet().stream()
                .filter(entry -> entry.getValue().equals(sessionId))
                .map(Map.Entry::getKey)
                .findFirst().get();
        Integer score = getTargetScore(sourceId);
        readyForMMByScore.add(score, sourceId);
    }

    public void stopMM(String sessionId) {
        String sourceId = onlineByID.entrySet().stream()
                .filter(entry -> entry.getValue().equals(sessionId))
                .map(Map.Entry::getKey)
                .findFirst().get();
        Integer score = getTargetScore(sourceId);
        System.out.println(score);
        System.out.println(sourceId);
        readyForMMByScore.put(score,
                readyForMMByScore.get(score).stream().filter(item -> !item.equals(sourceId))
                        .collect(
                                Collectors.toList()));
    }

    @Scheduled(fixedRateString = "10000")
    public void processMM() {
        log.info("Process MM");
        ArrayList<Integer> list = new ArrayList<>();
        readyForMMByScore.keySet().stream()
                .sorted()
                .forEachOrdered(list::add);

        for (int i = 0; i < list.size(); i++) {
            Integer score = list.get(i);
            List<String> ids = readyForMMByScore.get(score);
            if (ids.isEmpty()) {
                continue;
            }
            System.out.println("--------------------");
            System.out.println(score);
            System.out.println(ids);
            System.out.println("--------------------");
            ListIterator<String> iterator = ids.listIterator();
            String firstId = iterator.next();
            System.out.println("Itter");
            while (iterator.hasNext() && firstId != null) {
                String secondId = iterator.next();

                //TODo notify with battle
                List<Object> battle = challengeMM(firstId, secondId);
                publisher.publishEvent(new SendPrivate(battle));

                removeUserFromMM(score, firstId);
                removeUserFromMM(score, secondId);
                if (iterator.hasNext()) {
                    firstId = iterator.next();
                } else {
                    firstId = null;
                }
            }
            if (firstId != null){
                //process next rank
                for (int j = i-1; j>=0; j--) {
                    score = list.get(j);
                    ids = readyForMMByScore.get(score);
                    if (!ids.isEmpty()){
                        String secondId = ids.get(0);
                        //TODo notify with battle
                        List<Object> battle = challengeMM(firstId, secondId);
                        publisher.publishEvent(new SendPrivate(battle));

                        removeUserFromMM(score, firstId);
                        removeUserFromMM(score, secondId);
                    }
                }
            }

        }
    }

    private void removeUserFromMM(Integer score, String id){
        readyForMMByScore.put(score,
                readyForMMByScore.get(score).stream()
                        .filter(item -> !item.equals(id))
                        .collect(Collectors.toList()));
    }

    private List<Object> challengeMM(String sourceId, String targetId) {
        List<Object> result = new LinkedList<>();

        long matchId = System.currentTimeMillis();

        result.add(Map.of("target", sourceId,
                "data",
                Map.of("type", "BATTLE", "match", Map.of("id", matchId, "with", targetId))));

        result.add(Map.of("target", targetId,
                "data",
                Map.of("type", "BATTLE", "match", Map.of("id", matchId, "with", sourceId))));

        buffers.add(targetId,
                Map.of("type", "BATTLE", "match", Map.of("id", matchId, "with", sourceId)));
        buffers.add(sourceId,
                Map.of("type", "BATTLE", "match", Map.of("id", matchId, "with", targetId)));
        matches.put(matchId, new MatchDetails(sourceId, targetId));

        return result;
    }
}
