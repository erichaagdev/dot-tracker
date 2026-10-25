package dev.erichaag.dottracker;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** A clock that always reads {@link #NOW}, for tests that check what happens at a given time. */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

  public static final Instant NOW = Instant.parse("2026-10-25T17:00:00Z");

  @Bean
  Clock clock() {
    return Clock.fixed(NOW, ZoneId.of("America/Chicago"));
  }
}
