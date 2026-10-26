package dev.erichaag.dottracker.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import dev.erichaag.dottracker.PostgresTestConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// Migrates a schema of its own up to V2, adds rows like the ones production had before V3, then runs V3 over them, so
// the schema every other test uses is left alone. Flyway commits on its own connections, so this runs outside a test
// transaction and drops its schema afterwards.
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(PostgresTestConfiguration.class)
@Transactional(propagation = NOT_SUPPORTED)
class InviteEmailMigrationTest {

  private static final String SCHEMA = "invite_email_migration";

  private static final Instant NOW = Instant.parse("2026-10-25T17:00:00Z");

  @Autowired
  DataSource dataSource;

  @Autowired
  JdbcTemplate jdbc;

  @AfterEach
  void dropSchema() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
  }

  @Test
  void anAcceptedInviteTakesTheEmailOfWhoUsedItAndAnUnusedOneIsDeleted() {
    // Given
    migrateTo("2");
    var ana = member("ana@example.com", "Ana");
    var bea = member("bea@example.com", "Bea");
    invite("used", ana, bea);
    invite("unused", ana, null);

    // When
    migrateTo("3");

    // Then
    assertThat(jdbc.query("select code, email from " + SCHEMA + ".invite",
      (row, index) -> tuple(row.getString("code"), row.getString("email"))))
      .containsExactly(tuple("used", "bea@example.com"));
    assertThat(jdbc.queryForObject("""
      select is_nullable from information_schema.columns
      where table_schema = ? and table_name = 'invite' and column_name = 'email'
      """, String.class, SCHEMA))
      .isEqualTo("NO");
  }

  private void migrateTo(String version) {
    Flyway.configure().dataSource(dataSource).schemas(SCHEMA).target(version).load().migrate();
  }

  private UUID member(String email, String name) {
    return Objects.requireNonNull(jdbc.queryForObject(
      "insert into " + SCHEMA + ".member (email, display_name, role, joined_at, version) "
        + "values (?, ?, 'MEMBER', ?, 0) returning id", UUID.class, email, name, Timestamp.from(NOW)));
  }

  private void invite(String code, UUID createdBy, @Nullable UUID acceptedBy) {
    jdbc.update("insert into " + SCHEMA + ".invite "
        + "(code, created_by, created_at, expires_at, accepted_by, accepted_at, version) values (?, ?, ?, ?, ?, ?, 0)",
      code, createdBy, Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(60)), acceptedBy,
      acceptedBy == null ? null : Timestamp.from(NOW));
  }
}
