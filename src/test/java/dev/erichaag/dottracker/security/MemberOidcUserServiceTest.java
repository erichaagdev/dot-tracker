package dev.erichaag.dottracker.security;

import static dev.erichaag.dottracker.FixedClockConfiguration.NOW;
import static dev.erichaag.dottracker.member.Member.Role.ADMIN;
import static dev.erichaag.dottracker.member.Member.Role.MEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import dev.erichaag.dottracker.DotTrackerProperties;
import dev.erichaag.dottracker.FixedClockConfiguration;
import dev.erichaag.dottracker.PostgresTestConfiguration;
import dev.erichaag.dottracker.invite.Invite;
import dev.erichaag.dottracker.invite.InviteRepository;
import dev.erichaag.dottracker.invite.InviteService;
import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.member.MemberRepository;
import dev.erichaag.dottracker.member.MemberService;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.session.jdbc.config.annotation.web.http.JdbcHttpSessionConfiguration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@DataJpaTest(properties = "dot-tracker.admin-emails=other-admin@example.com, Boss@Example.com")
@AutoConfigureTestDatabase(replace = NONE)
@Import({
  PostgresTestConfiguration.class,
  FixedClockConfiguration.class,
  JdbcHttpSessionConfiguration.class,
  MemberOidcUserService.class,
  MemberService.class,
  InviteService.class,
})
@EnableConfigurationProperties(DotTrackerProperties.class)
class MemberOidcUserServiceTest {

  @Autowired
  MemberOidcUserService service;

  @Autowired
  MemberRepository members;

  @Autowired
  InviteRepository invites;

  MockHttpSession session = new MockHttpSession();

  @BeforeEach
  void startARequest() {
    var request = new MockHttpServletRequest();
    request.setSession(session);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @AfterEach
  void endTheRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void aMemberSignsInByTheirGoogleAccount() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN, "google-ana");

    // When
    var user = service.loadUser(signIn("google-ana", "ana.new.address@example.com", true, "Ana"));

    // Then
    assertThat(user.getName()).isEqualTo(id(ana).toString());
    assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
  }

  @Test
  void aMemberFoundByEmailHasTheirGoogleAccountLinked() {
    // Given
    var bea = save("bea@example.com", "Bea", MEMBER, null);

    // When
    var user = service.loadUser(signIn("google-bea", "Bea@Example.com", true, "Bea Lin"));

    // Then
    assertThat(user.getName()).isEqualTo(id(bea).toString());
    assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MEMBER");
    assertThat(bea.getGoogleSubject()).isEqualTo("google-bea");
    assertThat(bea.getDisplayName()).isEqualTo("Bea");
  }

  @Test
  void anUnverifiedEmailIsNotLinkedToAMember() {
    // Given
    var bea = save("bea@example.com", "Bea", MEMBER, null);

    // When / Then
    assertRefused(signIn("google-bea", "bea@example.com", false, "Bea"));
    assertThat(bea.getGoogleSubject()).isNull();
  }

  @Test
  void aMemberLinkedToOneGoogleAccountCannotBeReachedFromAnother() {
    // Given
    var bea = save("bea@example.com", "Bea", MEMBER, "google-bea");

    // When / Then
    assertRefused(signIn("google-someone-else", "bea@example.com", true, "Bea"));
    assertThat(bea.getGoogleSubject()).isEqualTo("google-bea");
  }

  @Test
  void anOpenInviteAddsANewMemberAndIsUsedUp() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN, "google-ana");
    var invite = invites.save(new Invite("invite-code", "cat@example.com", id(ana), NOW.minus(Duration.ofDays(13))));
    session.setAttribute(JoinController.INVITE_CODE, "invite-code");

    // When
    var user = service.loadUser(signIn("google-cat", "Cat@Example.com", true, "Cat Reyes"));

    // Then
    var cat = members.findByGoogleSubject("google-cat").orElseThrow();
    assertThat(cat.getEmail()).isEqualTo("cat@example.com");
    assertThat(cat.getDisplayName()).isEqualTo("Cat Reyes");
    assertThat(cat.getRole()).isEqualTo(MEMBER);
    assertThat(cat.getJoinedAt()).isEqualTo(NOW);
    assertThat(user.getName()).isEqualTo(id(cat).toString());
    assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MEMBER");
    assertThat(invite.getAcceptedBy()).isEqualTo(cat.getId());
    assertThat(invite.getAcceptedAt()).isEqualTo(NOW);
    assertThat(session.getAttribute(JoinController.INVITE_CODE)).isNull();
  }

  @Test
  void aNewMemberWithoutANameIsNamedAfterTheirEmail() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN, "google-ana");
    invites.save(new Invite("invite-code", "cat@example.com", id(ana), NOW));
    session.setAttribute(JoinController.INVITE_CODE, "invite-code");

    // When
    service.loadUser(signIn("google-cat", "cat@example.com", true, null));

    // Then
    assertThat(members.findByGoogleSubject("google-cat")).get()
      .extracting(Member::getDisplayName)
      .isEqualTo("cat@example.com");
  }

  @Test
  void anExpiredInviteIsRefused() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN, "google-ana");
    invites.save(new Invite("invite-code", "cat@example.com", id(ana), NOW.minus(Duration.ofDays(14))));
    session.setAttribute(JoinController.INVITE_CODE, "invite-code");

    // When / Then
    assertRefused(signIn("google-cat", "cat@example.com", true, "Cat"));
    assertThat(members.findByEmail("cat@example.com")).isEmpty();
  }

  @Test
  void aUsedInviteIsRefused() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN, "google-ana");
    var bea = save("bea@example.com", "Bea", MEMBER, "google-bea");
    invites.save(new Invite("invite-code", "cat@example.com", id(ana), NOW)).accept(id(bea), NOW);
    session.setAttribute(JoinController.INVITE_CODE, "invite-code");

    // When / Then
    assertRefused(signIn("google-cat", "cat@example.com", true, "Cat"));
    assertThat(members.findByEmail("cat@example.com")).isEmpty();
  }

  @Test
  void anInviteForAnotherEmailRefusesTheWrongAccountAndStaysForTheRightOne() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN, "google-ana");
    var invite = invites.save(new Invite("invite-code", "cat@example.com", id(ana), NOW));
    session.setAttribute(JoinController.INVITE_CODE, "invite-code");

    // When / Then
    assertRefused(signIn("google-cat-at-work", "cat@work.example.com", true, "Cat"), "wrong_account");
    assertThat(members.findByGoogleSubject("google-cat-at-work")).isEmpty();
    assertThat(invite.getAcceptedBy()).isNull();
    assertThat(session.getAttribute(JoinController.INVITE_CODE)).isEqualTo("invite-code");

    // When
    service.loadUser(signIn("google-cat", "cat@example.com", true, "Cat"));

    // Then
    var cat = members.findByGoogleSubject("google-cat").orElseThrow();
    assertThat(invite.getAcceptedBy()).isEqualTo(cat.getId());
    assertThat(session.getAttribute(JoinController.INVITE_CODE)).isNull();
  }

  @Test
  void anInviteIsNotUsedByAnUnverifiedEmail() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN, "google-ana");
    var invite = invites.save(new Invite("invite-code", "cat@example.com", id(ana), NOW));
    session.setAttribute(JoinController.INVITE_CODE, "invite-code");

    // When / Then
    assertRefused(signIn("google-cat", "cat@example.com", false, "Cat"));
    assertThat(members.findByEmail("cat@example.com")).isEmpty();
    assertThat(invite.getAcceptedBy()).isNull();
  }

  @Test
  void aMemberHoldingSomeoneElsesInviteSignsInAsThemselvesAndLeavesItOpen() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN, "google-ana");
    var bea = save("bea@example.com", "Bea", MEMBER, "google-bea");
    var invite = invites.save(new Invite("invite-code", "cat@example.com", id(ana), NOW));
    session.setAttribute(JoinController.INVITE_CODE, "invite-code");

    // When
    var user = service.loadUser(signIn("google-bea", "bea@example.com", true, "Bea"));

    // Then
    assertThat(user.getName()).isEqualTo(id(bea).toString());
    assertThat(invite.getAcceptedBy()).isNull();
  }

  @Test
  void anAdminEmailJoinsAsAnAdminWithoutAnInvite() {
    // When
    var user = service.loadUser(signIn("google-boss", "boss@example.com", true, "Boss"));

    // Then
    var boss = members.findByGoogleSubject("google-boss").orElseThrow();
    assertThat(boss.getRole()).isEqualTo(ADMIN);
    assertThat(user.getName()).isEqualTo(id(boss).toString());
    assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
  }

  @Test
  void anUnverifiedAdminEmailIsRefused() {
    // When / Then
    assertRefused(signIn("google-boss", "boss@example.com", false, "Boss"));
    assertThat(members.findByEmail("boss@example.com")).isEmpty();
  }

  @Test
  void aStrangerWithoutAnInviteIsRefused() {
    // When / Then
    assertRefused(signIn("google-stranger", "stranger@example.com", true, "Stranger"));
    assertThat(members.findByEmail("stranger@example.com")).isEmpty();
  }

  private void assertRefused(OidcUserRequest request) {
    assertRefused(request, "not_invited");
  }

  private void assertRefused(OidcUserRequest request, String errorCode) {
    assertThatExceptionOfType(OAuth2AuthenticationException.class)
      .isThrownBy(() -> service.loadUser(request))
      .satisfies(e -> assertThat(e.getError().getErrorCode()).isEqualTo(errorCode));
  }

  private Member save(String email, String name, Role role, @Nullable String googleSubject) {
    var member = new Member(email, name, role, NOW);
    if (googleSubject != null) {
      member.linkGoogleAccount(googleSubject);
    }
    return members.save(member);
  }

  private static UUID id(Member member) {
    return Objects.requireNonNull(member.getId());
  }

  /** What Google answers with. The registration has no user info address, so only the ID token is read. */
  private static OidcUserRequest signIn(String subject, String email, boolean emailVerified,
    @Nullable String name) {
    var registration = ClientRegistration.withRegistrationId("google")
      .clientId("client-id")
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
      .authorizationUri("https://accounts.example.com/authorize")
      .tokenUri("https://accounts.example.com/token")
      .build();
    var idToken = OidcIdToken.withTokenValue("id-token")
      .subject(subject)
      .claim("email", email)
      .claim("email_verified", emailVerified)
      .claims(claims -> {
        if (name != null) {
          claims.put("name", name);
        }
      })
      .issuedAt(NOW)
      .expiresAt(NOW.plus(Duration.ofHours(1)))
      .build();
    var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", NOW,
      NOW.plus(Duration.ofHours(1)), Set.of("openid", "email", "profile"));
    return new OidcUserRequest(registration, accessToken, idToken);
  }
}
