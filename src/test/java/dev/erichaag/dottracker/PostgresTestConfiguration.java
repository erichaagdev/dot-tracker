package dev.erichaag.dottracker;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Connects a test context to Postgres 18 in a container, with the real Flyway migrations applied. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

  // Static, so every cached test context shares one container instead of starting its own
  private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return postgres;
  }
}
