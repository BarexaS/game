package com.barexas.game.model;

import org.springframework.context.ApplicationEvent;

public class SendPrivate extends ApplicationEvent {

    /**
     * Create a new {@code ApplicationEvent}.
     *
     * @param source the object on which the event initially occurred or with which the event is
     * associated (never {@code null})
     */
    public SendPrivate(Object source) {
        super(source);
    }
}
