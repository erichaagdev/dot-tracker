package dev.erichaag.dottracker.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

// Building the native image and running it against Postgres is what shows the whole set is complete
class SessionRuntimeHintsTest {

  private final RuntimeHints hints = new RuntimeHints();

  @Test
  void aSignedInMemberCanBeSerializedInANativeImage() {
    // When
    new SessionRuntimeHints().registerHints(hints, getClass().getClassLoader());

    // Then
    assertThat(serializable(MemberOidcUser.class)
      .and(serializable(DefaultOidcUser.class))
      .and(serializable(OidcIdToken.class))
      .and(serializable(Instant.class)))
      .accepts(hints);
  }

  @Test
  void googlesAuthorizationRequestCanBeSerializedInANativeImage() {
    // When
    new SessionRuntimeHints().registerHints(hints, getClass().getClassLoader());

    // Then
    assertThat(serializable(OAuth2AuthorizationRequest.class)).accepts(hints);
  }

  private static Predicate<RuntimeHints> serializable(Class<?> type) {
    return RuntimeHintsPredicates.reflection().onJavaSerialization(type, true);
  }
}
