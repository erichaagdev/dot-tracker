package dev.erichaag.dottracker.invite;

import static dev.erichaag.dottracker.FixedClockConfiguration.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import dev.erichaag.dottracker.FixedClockConfiguration;
import dev.erichaag.dottracker.PostgresTestConfiguration;
import dev.erichaag.dottracker.invite.InviteService.OpenInvite;
import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.member.MemberRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = NONE)
@Import({PostgresTestConfiguration.class, FixedClockConfiguration.class, InviteService.class})
class InviteServiceTest {

  @Autowired
  InviteService service;

  @Autowired
  InviteRepository repository;

  @Autowired
  MemberRepository members;

  @Autowired
  TestEntityManager entityManager;

  @Autowired
  EntityManagerFactory entityManagerFactory;

  UUID ana = UUID.randomUUID();

  UUID bea = UUID.randomUUID();

  @BeforeEach
  void saveMembers() {
    ana = Objects.requireNonNull(members.save(new Member("ana@example.com", "Ana", Role.ADMIN, NOW)).getId());
    bea = Objects.requireNonNull(members.save(new Member("bea@example.com", "Bea", Role.MEMBER, NOW)).getId());
  }

  @Test
  void aNewInviteIsForItsEmailHasAUrlSafeCodeAndExpiresIn14Days() {
    // When
    var created = service.create(" Kim@Example.com ", ana);

    // Then
    var invite = created.invite();
    assertThat(invite.getEmail()).isEqualTo("kim@example.com");
    assertThat(invite.getCode()).matches("[A-Za-z0-9_-]{22}");
    assertThat(invite.getCreatedBy()).isEqualTo(ana);
    assertThat(invite.getCreatedAt()).isEqualTo(NOW);
    assertThat(invite.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    assertThat(created.createdByName()).isEqualTo("Ana");
    assertThat(repository.findByCode(invite.getCode())).isPresent();
  }

  @Test
  void everyInviteGetsItsOwnCode() {
    // When
    var first = service.create("kim@example.com", ana);
    var second = service.create("lee@example.com", ana);

    // Then
    assertThat(first.invite().getCode()).isNotEqualTo(second.invite().getCode());
  }

  @Test
  void anInviteForSomeoneAlreadyInTheGroupIsRefused() {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> service.create("Bea@Example.com", ana))
      .withMessage("bea@example.com is already in the group.");
    assertThat(repository.count()).isZero();
  }

  @Test
  void aSecondOpenInviteForTheSameEmailIsRefused() {
    // Given
    save("open", "kim@example.com", NOW.minus(Duration.ofDays(1)));

    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> service.create("KIM@example.com", bea))
      .withMessage("kim@example.com already has an open invite. Copy or share its link from the list below.");
    assertThat(repository.count()).isOne();
  }

  @Test
  void anEmailWhoseInviteExpiredCanBeInvitedAgain() {
    // Given
    save("expired", "kim@example.com", NOW.minus(Duration.ofDays(14)));

    // When
    var created = service.create("kim@example.com", ana);

    // Then
    assertThat(created.invite().isOpen(NOW)).isTrue();
    assertThat(repository.count()).isEqualTo(2);
  }

  @Test
  void anInviteForSomethingThatIsNotAnEmailIsRefused() {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> service.create("kim@example", ana))
      .withMessage("kim@example isn't an email address. Check it for a typo and try again.");
    assertThat(repository.count()).isZero();
  }

  @Test
  void onlyOpenInvitesAreListedNewestFirstWithWhoMadeThem() {
    // Given
    save("older", "older@example.com", NOW.minus(Duration.ofDays(3)));
    repository.save(new Invite("newer", "newer@example.com", bea, NOW.minus(Duration.ofDays(1))));
    save("expired", "expired@example.com", NOW.minus(Duration.ofDays(14)));
    save("used", "used@example.com", NOW.minus(Duration.ofDays(2))).accept(bea, NOW);

    // When
    var open = service.findOpen();

    // Then
    assertThat(open)
      .extracting(each -> each.invite().getCode(), each -> each.invite().getEmail(), OpenInvite::createdByName)
      .containsExactly(tuple("newer", "newer@example.com", "Bea"), tuple("older", "older@example.com", "Ana"));
  }

  @Test
  void openInvitesLoadInTwoQueriesHoweverManyAdminsMadeThem() {
    // Given
    var cat = Objects.requireNonNull(members.save(new Member("cat@example.com", "Cat", Role.ADMIN, NOW)).getId());
    save("one", "one@example.com", NOW);
    repository.save(new Invite("two", "two@example.com", bea, NOW));
    repository.save(new Invite("three", "three@example.com", cat, NOW));
    save("four", "four@example.com", NOW);
    var statistics = freshStatistics();

    // When
    var open = service.findOpen();

    // Then
    assertThat(open).extracting(OpenInvite::createdByName).containsExactlyInAnyOrder("Ana", "Bea", "Cat", "Ana");
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  void anInviteIsOpenUntil14DaysAfterItWasMade() {
    // Given
    save("fresh", NOW.minus(Duration.ofDays(14)).plusSeconds(1));
    save("expired", NOW.minus(Duration.ofDays(14)));

    // When / Then
    assertThat(service.isOpen("fresh")).isTrue();
    assertThat(service.isOpen("expired")).isFalse();
  }

  @Test
  void aUsedOrUnknownCodeIsNotOpen() {
    // Given
    save("used", NOW).accept(bea, NOW);

    // When / Then
    assertThat(service.isOpen("used")).isFalse();
    assertThat(service.isOpen("unknown")).isFalse();
    assertThat(service.findOpen("used")).isEmpty();
  }

  @Test
  void anOpenInviteIsFoundByItsCode() {
    // Given
    var invite = save("open", NOW);

    // When / Then
    assertThat(service.getOpen("open").getId()).isEqualTo(invite.getId());
    assertThat(service.findOpen("open")).get().extracting(Invite::getId).isEqualTo(invite.getId());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"expired", "used", "unknown"})
  void anExpiredUsedUnknownOrMissingCodeHasNoOpenInvite(@Nullable String code) {
    // Given
    save("expired", NOW.minus(Duration.ofDays(14)));
    save("used", NOW).accept(bea, NOW);

    // When / Then
    assertThatExceptionOfType(NoSuchElementException.class)
      .isThrownBy(() -> service.getOpen(code))
      .withMessage("This invite has expired or was already used. Ask for a new one.");
  }

  @Test
  void acceptingAnInviteRecordsWhoUsedIt() {
    // Given
    save("code", NOW);

    // When
    service.accept("code", bea);

    // Then
    var invite = repository.findByCode("code").orElseThrow();
    assertThat(invite.getAcceptedBy()).isEqualTo(bea);
    assertThat(invite.getAcceptedAt()).isEqualTo(NOW);
  }

  @Test
  void acceptingAnUnknownCodeIsRefused() {
    // When / Then
    assertThatExceptionOfType(NoSuchElementException.class)
      .isThrownBy(() -> service.accept("unknown", bea))
      .withMessage("That invite link doesn't work. Ask for a new one.");
  }

  @Test
  void deletingAnInviteRevokesIt() {
    // Given
    var invite = save("code", NOW);

    // When
    service.delete(Objects.requireNonNull(invite.getId()));

    // Then
    assertThat(service.isOpen("code")).isFalse();
    assertThat(repository.findByCode("code")).isEmpty();
  }

  @Test
  void deletingAnInviteThatIsAlreadyGoneDoesNothing() {
    // When
    service.delete(UUID.randomUUID());

    // Then
    assertThat(repository.count()).isZero();
  }

  // Saved rows would otherwise come back from the session without a query
  private Statistics freshStatistics() {
    entityManager.flush();
    entityManager.clear();
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    return statistics;
  }

  private Invite save(String code, Instant createdAt) {
    return save(code, code + "@example.com", createdAt);
  }

  private Invite save(String code, String email, Instant createdAt) {
    return repository.save(new Invite(code, email, ana, createdAt));
  }
}
