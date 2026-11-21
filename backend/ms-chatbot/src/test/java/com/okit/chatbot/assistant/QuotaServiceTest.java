package com.okit.chatbot.assistant;

import com.okit.chatbot.config.ChatbotProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class QuotaServiceTest {

    @Test
    void compteurRemisAZeroLeLendemain() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-21T10:00:00Z"));
        QuotaService quota = new QuotaService(props("polytech"), clock);

        assertThat(quota.consommer("a")).isTrue();
        assertThat(quota.consommer("a")).isFalse();

        clock.avancer(Duration.ofDays(1));
        assertThat(quota.consommer("a")).isTrue();
    }

    @Test
    void pasDeLimiteEnModeSimule() {
        QuotaService quota = new QuotaService(props("mock"), Clock.systemUTC());
        for (int i = 0; i < 10; i++) {
            assertThat(quota.consommer("a")).isTrue();
        }
    }

    private static ChatbotProperties props(String mode) {
        return new ChatbotProperties(mode, 1, 10, 1000, null);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void avancer(Duration d) {
            instant = instant.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
