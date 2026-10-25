package dev.erichaag.dottracker;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

  /** The group's own time zone, which decides what today is when a book is started or finished. */
  @Bean
  public Clock clock() {
    return Clock.system(ZoneId.of("America/Chicago"));
  }
}
