package com.eyeqlytics.fieldops.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes domain events to a Redis Pub/Sub channel.
 * Events are sent only AFTER the DB transaction commits, so subscribers never
 * see an event for a change that was rolled back.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    public static final String CHANNEL = "fieldops.events";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public void publish(DomainEvent event) {
        Runnable send = () -> {
            try {
                redis.convertAndSend(CHANNEL, mapper.writeValueAsString(event));
            } catch (Exception e) {
                log.warn("Failed to publish event {}: {}", event.type(), e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }
}
