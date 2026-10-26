package dev.erichaag.dottracker;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoDriversForInteractiveAuthException;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoIntegratedAuthException;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * Registers what Hibernate and Flyway reach by reflection beyond the native image metadata they come with, since a
 * native image can only reach what it was built with. Hibernate loads several entities at once through an array of
 * their ids, and every id is a UUID. When it can't connect, Flyway asks each of its SQL exception types whether it
 * fits through a static method, and without these the real reason for the failed connection is lost.
 */
final class PersistenceRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
    hints.reflection().registerType(UUID[].class);
    List.of(FlywaySqlNoIntegratedAuthException.class, FlywaySqlNoDriversForInteractiveAuthException.class)
      .forEach(type -> hints.reflection().registerType(type, hint -> hint.withMethod("isFlywaySpecificVersionOf",
        List.of(TypeReference.of(SQLException.class)), ExecutableMode.INVOKE)));
  }
}
