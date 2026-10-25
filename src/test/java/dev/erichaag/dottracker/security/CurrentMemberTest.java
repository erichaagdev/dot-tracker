package dev.erichaag.dottracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.erichaag.dottracker.member.Member.Role;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

class CurrentMemberTest {

  private static final UUID ANA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3d");

  CurrentMember currentMember = new CurrentMember();

  @AfterEach
  void signOut() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void aGoogleSignInIsReadAsTheMembersId() {
    // Given
    var idToken = OidcIdToken.withTokenValue("id-token")
      .subject("google-ana")
      .issuedAt(Instant.EPOCH)
      .expiresAt(Instant.EPOCH.plusSeconds(60))
      .build();
    var user = new MemberOidcUser(ANA, Role.ADMIN, idToken, null);
    SecurityContextHolder.getContext()
      .setAuthentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));

    // When / Then
    assertThat(currentMember.id()).isEqualTo(ANA);
  }

  @Test
  void aDemoSignInIsReadAsTheMembersId() {
    // Given
    var user = User.withUsername(ANA.toString()).password("{noop}demo").roles("ADMIN").build();
    SecurityContextHolder.getContext()
      .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));

    // When / Then
    assertThat(currentMember.id()).isEqualTo(ANA);
  }

  @Test
  void nobodySignedInIsRefused() {
    // Given
    SecurityContextHolder.getContext().setAuthentication(
      new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    // When / Then
    assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class).isThrownBy(currentMember::id);
  }
}
