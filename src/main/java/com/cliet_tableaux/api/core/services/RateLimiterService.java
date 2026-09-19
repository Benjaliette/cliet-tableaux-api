package com.cliet_tableaux.api.core.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class RateLimiterService {
    public static final int DEFAULT_MAX_REQUESTS = 5;
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);

    private static final long MAX_TRACKED_KEYS = 100_000;
    private static final Duration KEY_EVICTION_TTL = Duration.ofHours(1);

    private final Cache<String, Deque<Instant>> callsByKey = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_KEYS)
            .expireAfterWrite(KEY_EVICTION_TTL)
            .build();

    public boolean isAllowed(String key, int maxCalls, Duration window) {
        Deque<Instant> calls = callsByKey.get(key, k -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();
        Instant windowStart = now.minus(window);

        synchronized (calls) {
            while (!calls.isEmpty() && calls.peekFirst().isBefore(windowStart)) {
                calls.pollFirst();
            }
            if (calls.size() >= maxCalls) {
                return false;
            }
            calls.addLast(now);
            return true;
        }
    }

    long trackedKeyCount() {
        callsByKey.cleanUp();
        return callsByKey.estimatedSize();
    }
}
