package dev.erichaag.dottracker.security;

import java.net.URL;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/**
 * Registers what the session holds for JDK serialization, beyond the types Spring registers itself. Spring Session JDBC
 * stores each session attribute that way, and a native image can only serialize a type it was built to, along with
 * every serializable superclass and every type its fields hold.
 */
final class SessionRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
    List.of(
      // Google sign-in keeps its authorization request in the session until Google sends the person back
      OAuth2AuthorizationRequest.class, AuthorizationGrantType.class, OAuth2AuthorizationResponseType.class,
      // A signed-in member's security context holds their ID token and user info, whose claims include a URL, a
      // Boolean and instants
      MemberOidcUser.class, DefaultOidcUser.class, DefaultOAuth2User.class, OidcIdToken.class,
      AbstractOAuth2Token.class, OidcUserInfo.class, UUID.class, URL.class, Boolean.class, Instant.class,
      // Both keep their claims, parameters and scopes in these
      HashMap.class, LinkedHashMap.class, HashSet.class, LinkedHashSet.class
    ).forEach(hints.reflection()::registerJavaSerialization);
    // Claims are held in an unmodifiable map, and an Instant is written as a java.time.Ser that reads back as one
    List.of("java.util.Collections$UnmodifiableMap", "java.time.Ser").forEach(type -> hints.reflection()
      .registerType(TypeReference.of(type), hint -> hint.withJavaSerialization(true)));
  }
}
