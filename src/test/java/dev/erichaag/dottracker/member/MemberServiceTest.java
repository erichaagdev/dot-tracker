package dev.erichaag.dottracker.member;

import static dev.erichaag.dottracker.FixedClockConfiguration.NOW;
import static dev.erichaag.dottracker.member.Member.Role.ADMIN;
import static dev.erichaag.dottracker.member.Member.Role.MEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import dev.erichaag.dottracker.DotTrackerProperties;
import dev.erichaag.dottracker.FixedClockConfiguration;
import dev.erichaag.dottracker.PostgresTestConfiguration;
import dev.erichaag.dottracker.invite.InviteService;
import dev.erichaag.dottracker.member.Member.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.config.annotation.web.http.JdbcHttpSessionConfiguration;

// JdbcHttpSessionConfiguration is what Spring Boot imports for the app, so sessions commit on their own connection as
// they do in production, and each test deletes the ones it saved
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({
  PostgresTestConfiguration.class,
  FixedClockConfiguration.class,
  JdbcHttpSessionConfiguration.class,
  MemberService.class,
  InviteService.class,
})
@EnableConfigurationProperties(DotTrackerProperties.class)
class MemberServiceTest {

  @Autowired
  MemberService service;

  @Autowired
  MemberRepository repository;

  @Autowired
  JdbcIndexedSessionRepository sessions;

  List<String> sessionIds = new ArrayList<>();

  @AfterEach
  void deleteSessions() {
    sessionIds.forEach(sessions::deleteById);
  }

  @Test
  void promotingAMemberMakesThemAnAdmin() {
    // Given
    save("ana@example.com", "Ana", ADMIN);
    var bea = save("bea@example.com", "Bea", MEMBER);

    // When
    var promoted = service.changeRole(id(bea), ADMIN);

    // Then
    assertThat(promoted.getRole()).isEqualTo(ADMIN);
  }

  @Test
  void anAdminCanBeDemotedWhileAnotherAdminRemains() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN);
    save("bea@example.com", "Bea", ADMIN);

    // When
    var demoted = service.changeRole(id(ana), MEMBER);

    // Then
    assertThat(demoted.getRole()).isEqualTo(MEMBER);
  }

  @Test
  void theLastAdminCannotBeDemoted() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN);
    save("bea@example.com", "Bea", MEMBER);

    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> service.changeRole(id(ana), MEMBER))
      .withMessage("The group needs at least one admin, so make someone else an admin before changing this.");
    assertThat(repository.findById(id(ana))).get().extracting(Member::getRole).isEqualTo(ADMIN);
  }

  @Test
  void makingTheLastAdminAnAdminAgainChangesNothing() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN);

    // When
    var unchanged = service.changeRole(id(ana), ADMIN);

    // Then
    assertThat(unchanged.getRole()).isEqualTo(ADMIN);
  }

  @Test
  void changingARoleSignsThatMemberOutEverywhere() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN);
    var bea = save("bea@example.com", "Bea", MEMBER);
    var beaOnHerPhone = signIn(bea);
    var beaOnHerLaptop = signIn(bea);
    var anaOnHerPhone = signIn(ana);

    // When
    service.changeRole(id(bea), ADMIN);

    // Then
    assertThat(sessions.findById(beaOnHerPhone)).isNull();
    assertThat(sessions.findById(beaOnHerLaptop)).isNull();
    assertThat(sessions.findById(anaOnHerPhone)).isNotNull();
  }

  @Test
  void keepingTheSameRoleKeepsTheMemberSignedIn() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN);
    var anaOnHerPhone = signIn(ana);

    // When
    service.changeRole(id(ana), ADMIN);

    // Then
    assertThat(sessions.findById(anaOnHerPhone)).isNotNull();
  }

  @Test
  void aRefusedRoleChangeKeepsTheMemberSignedIn() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN);
    var anaOnHerPhone = signIn(ana);

    // When / Then
    assertThatIllegalArgumentException().isThrownBy(() -> service.changeRole(id(ana), MEMBER));
    assertThat(sessions.findById(anaOnHerPhone)).isNotNull();
  }

  @Test
  void changingTheRoleOfSomeoneOutsideTheGroupIsRefused() {
    // When / Then
    assertThatExceptionOfType(NoSuchElementException.class)
      .isThrownBy(() -> service.changeRole(UUID.randomUUID(), ADMIN))
      .withMessage("That person isn't in the group.");
  }

  @Test
  void membersAreListedByName() {
    // Given
    save("cat@example.com", "Cat", MEMBER);
    save("ana@example.com", "Ana", ADMIN);
    save("bea@example.com", "Bea", MEMBER);

    // When
    var members = service.findAll();

    // Then
    assertThat(members).extracting(Member::getDisplayName).containsExactly("Ana", "Bea", "Cat");
  }

  @Test
  void aMemberIsFoundByEmailHoweverItIsTyped() {
    // Given
    var ana = save("ana@example.com", "Ana", ADMIN);

    // When
    var found = service.findByEmail(" Ana@Example.com");

    // Then
    assertThat(found).get().extracting(Member::getId).isEqualTo(ana.getId());
  }

  /** Saves a session the way a sign-in does, under the member's id as its principal name. */
  private String signIn(Member member) {
    var id = save(sessions, id(member).toString());
    sessionIds.add(id);
    return id;
  }

  // Generic, since the repository's own session type isn't public
  private static <S extends Session> String save(SessionRepository<S> repository, String principalName) {
    var session = repository.createSession();
    session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);
    repository.save(session);
    return session.getId();
  }

  private Member save(String email, String name, Role role) {
    return repository.save(new Member(email, name, role, NOW));
  }

  private static UUID id(Member member) {
    return Objects.requireNonNull(member.getId());
  }
}
