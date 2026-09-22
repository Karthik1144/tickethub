package com.tickethub.seating.service;

import com.tickethub.seating.dto.SeatUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fan-out of live seat updates over SSE.
 * Events are published only AFTER_COMMIT, so a client never sees uncommitted state.
 * With several instances this needs a shared broker (see Doc 03, scaling note).
 */
@Component
public class SeatUpdatePublisher {

    private static final Logger log = LoggerFactory.getLogger(SeatUpdatePublisher.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, List<SseEmitter>> emittersByShow = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long showId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> emitters = emittersByShow.computeIfAbsent(showId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> remove(showId, emitter));
        emitter.onTimeout(() -> remove(showId, emitter));
        emitter.onError(e -> remove(showId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("showId", showId)));
        } catch (IOException ex) {
            remove(showId, emitter);
        }
        return emitter;
    }

    /** Called after the seat transaction commits. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatUpdateAfterCommit(SeatUpdateEvent event) {
        broadcast(event);
    }

    public void broadcast(SeatUpdateEvent event) {
        List<SseEmitter> emitters = emittersByShow.get(event.showId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("seat-update").data(event));
            } catch (Exception ex) {
                log.debug("Dropping dead SSE emitter for show {}", event.showId());
                remove(event.showId(), emitter);
            }
        }
    }

    public int subscriberCount(Long showId) {
        List<SseEmitter> emitters = emittersByShow.get(showId);
        return emitters == null ? 0 : emitters.size();
    }

    private void remove(Long showId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByShow.get(showId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}
