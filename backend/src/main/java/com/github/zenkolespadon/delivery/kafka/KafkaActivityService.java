package com.github.zenkolespadon.delivery.kafka;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class KafkaActivityService {

    private static final Duration GPS_RATE_WINDOW = Duration.ofSeconds(30);
    private static final int RECENT_TOPIC_LIMIT = 8;

    private final AtomicLong gpsEventsProduced = new AtomicLong();
    private final AtomicLong gpsEventsConsumed = new AtomicLong();
    private final AtomicLong deliveryEventsProduced = new AtomicLong();
    private final AtomicLong etaEventsProduced = new AtomicLong();
    private final AtomicLong geofenceEventsProduced = new AtomicLong();
    private final AtomicLong deliveryAlertsConsumed = new AtomicLong();
    private final AtomicLong deadLetterEventsProduced = new AtomicLong();
    private final Deque<Instant> consumedGpsTimestamps = new ArrayDeque<>();
    private final Deque<String> recentlyTouchedTopics = new ArrayDeque<>();

    public void gpsProduced(String topic) {
        gpsEventsProduced.incrementAndGet();
        touch(topic);
    }

    public void gpsConsumed(String topic) {
        gpsEventsConsumed.incrementAndGet();
        synchronized (consumedGpsTimestamps) {
            consumedGpsTimestamps.addLast(Instant.now());
            pruneGpsTimestamps();
        }
        touch(topic);
    }

    public void deliveryProduced(String topic) {
        deliveryEventsProduced.incrementAndGet();
        touch(topic);
    }

    public void etaProduced(String topic) {
        etaEventsProduced.incrementAndGet();
        touch(topic);
    }

    public void geofenceProduced(String topic) {
        geofenceEventsProduced.incrementAndGet();
        touch(topic);
    }

    public void deliveryAlertConsumed(String topic) {
        deliveryAlertsConsumed.incrementAndGet();
        touch(topic);
    }

    public void deadLetterProduced(String topic) {
        deadLetterEventsProduced.incrementAndGet();
        touch(topic);
    }

    public KafkaActivitySnapshot snapshot() {
        return new KafkaActivitySnapshot(
                gpsEventsProduced.get(),
                gpsEventsConsumed.get(),
                deliveryEventsProduced.get(),
                etaEventsProduced.get(),
                geofenceEventsProduced.get(),
                deliveryAlertsConsumed.get(),
                deadLetterEventsProduced.get(),
                gpsEventsPerSecond(),
                recentTopics(),
                Instant.now()
        );
    }

    private double gpsEventsPerSecond() {
        synchronized (consumedGpsTimestamps) {
            pruneGpsTimestamps();
            return consumedGpsTimestamps.size() / (double) GPS_RATE_WINDOW.toSeconds();
        }
    }

    private void pruneGpsTimestamps() {
        Instant cutoff = Instant.now().minus(GPS_RATE_WINDOW);

        while (!consumedGpsTimestamps.isEmpty() && consumedGpsTimestamps.peekFirst().isBefore(cutoff)) {
            consumedGpsTimestamps.removeFirst();
        }
    }

    private void touch(String topic) {
        synchronized (recentlyTouchedTopics) {
            recentlyTouchedTopics.remove(topic);
            recentlyTouchedTopics.addFirst(topic);

            while (recentlyTouchedTopics.size() > RECENT_TOPIC_LIMIT) {
                recentlyTouchedTopics.removeLast();
            }
        }
    }

    private List<String> recentTopics() {
        synchronized (recentlyTouchedTopics) {
            return new ArrayList<>(new LinkedHashSet<>(recentlyTouchedTopics));
        }
    }
}
