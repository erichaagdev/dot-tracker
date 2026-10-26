package dev.erichaag.dottracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoDriversForInteractiveAuthException;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoIntegratedAuthException;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class PersistenceRuntimeHintsTest {

  private final RuntimeHints hints = new RuntimeHints();

  @Test
  void hibernateCanCreateAnArrayOfIdsInANativeImage() {
    // When
    new PersistenceRuntimeHints().registerHints(hints, getClass().getClassLoader());

    // Then
    assertThat(RuntimeHintsPredicates.reflection().onType(UUID[].class)).accepts(hints);
  }

  @Test
  void flywayCanTellWhyAConnectionFailedInANativeImage() throws Exception {
    // When
    new PersistenceRuntimeHints().registerHints(hints, getClass().getClassLoader());

    // Then
    assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
      FlywaySqlNoIntegratedAuthException.class.getMethod("isFlywaySpecificVersionOf", SQLException.class))
      .and(RuntimeHintsPredicates.reflection().onMethodInvocation(
        FlywaySqlNoDriversForInteractiveAuthException.class.getMethod("isFlywaySpecificVersionOf", SQLException.class))))
      .accepts(hints);
  }
}
