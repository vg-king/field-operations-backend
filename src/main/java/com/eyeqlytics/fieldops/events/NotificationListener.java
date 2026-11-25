package com.eyeqlytics.fieldops.events;

import com.eyeqlytics.fieldops.alert.AlertDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Subscriber on the Redis events channel: logs every event and fans it out as user alerts. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener implements MessageListener {

    private final AlertDispatcher dispatcher;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("[NOTIFY] {}", body);
        dispatcher.handle(body);
    }
}
