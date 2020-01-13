package com.barexas.game.model;

import lombok.Data;

@Data
public class MatchDetails {
    private String firstId;
    private boolean firstApprove;
    private String secondId;
    private boolean secondApprove;

    public MatchDetails(String firstId, String secondId) {
        this.firstId = firstId;
        this.secondId = secondId;
    }

    public boolean isReady() {
        return firstApprove && secondApprove;
    }
}
