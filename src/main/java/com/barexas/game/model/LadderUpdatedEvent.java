package com.barexas.game.model;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class LadderUpdatedEvent extends ApplicationEvent {
    /**
     * Create a new {@code ApplicationEvent}.
     *
     * @param source the object on which the event initially occurred or with
     *               which the event is associated (never {@code null})
     */
    public LadderUpdatedEvent(Object source) {
        super(source);
    }
}
