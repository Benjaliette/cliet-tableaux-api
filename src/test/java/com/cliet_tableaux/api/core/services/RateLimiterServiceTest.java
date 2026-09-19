package com.cliet_tableaux.api.core.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterServiceTest {

    private final RateLimiterService rateLimiterService = new RateLimiterService();

    @Test
    void isAllowed_underLimit_returnsTrue() {
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiterService.isAllowed("key-under-limit", 5, Duration.ofMinutes(15))).isTrue();
        }
    }

    @Test
    void isAllowed_atLimit_returnsFalse() {
        for (int i = 0; i < 5; i++) {
            rateLimiterService.isAllowed("key-at-limit", 5, Duration.ofMinutes(15));
        }

        assertThat(rateLimiterService.isAllowed("key-at-limit", 5, Duration.ofMinutes(15))).isFalse();
    }

    @Test
    void isAllowed_afterWindowExpires_allowsAgain() throws InterruptedException {
        Duration shortWindow = Duration.ofMillis(50);
        for (int i = 0; i < 3; i++) {
            rateLimiterService.isAllowed("key-short-window", 3, shortWindow);
        }
        assertThat(rateLimiterService.isAllowed("key-short-window", 3, shortWindow)).isFalse();

        Thread.sleep(100);

        assertThat(rateLimiterService.isAllowed("key-short-window", 3, shortWindow)).isTrue();
    }

    @Test
    void trackedKeyCount_reflectsDistinctKeysSeen() {
        rateLimiterService.isAllowed("compteur-1", 5, Duration.ofMinutes(15));
        rateLimiterService.isAllowed("compteur-2", 5, Duration.ofMinutes(15));
        rateLimiterService.isAllowed("compteur-3", 5, Duration.ofMinutes(15));

        assertThat(rateLimiterService.trackedKeyCount()).isEqualTo(3);
    }
}
