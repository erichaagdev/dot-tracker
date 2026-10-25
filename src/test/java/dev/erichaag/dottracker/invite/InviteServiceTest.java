package dev.erichaag.dottracker.invite;

import static dev.erichaag.dottracker.FixedClockConfiguration.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import dev.erichaag.dottracker.FixedClockConfiguration;
import dev.erichaag.dottracker.PostgresTestConfiguration;
import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.member.MemberRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({PostgresTestConfiguration.class, FixedClockConfiguration.class, InviteService.class})
class InviteServiceTest {

  @Autowired
  InviteService service;

  @Autowired
  InviteRepository repository;

  @Autowired
  MemberRepository members;

  UUID ana = UUID.randomUUID();

  UUID bea = UUID.randomUUID();

  @BeforeEach
  void saveMembers() {
    ana = Objects.requireNonNull(members.save(new Member("ana@example.com", "Ana", Role.ADMIN, NOW)).getId());
    bea = Objects.requireNonNull(members.save(new Member("bea@example.com", "Bea", Role.MEMBER, NOW)).getId());
  }

  @Test
  void aNewInviteHasAUrlSafeCodeAndExpiresIn14Days() {
    // When
    var invite = service.create(ana);

    // Then
    assertThat(invite.getCode()).matches("[A-Za-z0-9_-]{22}");
    assertThat(invite.getCreatedBy()).isEqualTo(ana);
    assertThat(invite.getCreatedAt()).isEqualTo(NOW);
    assertThat(invite.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
  }

  @Test
  void everyInviteGetsItsOwnCode() {
    // When
    var first = service.create(ana);
    var second = service.create(ana);

    // Then
    assertThat(first.getCode()).isNotEqualTo(second.getCode());
  }

  @Test
  void onlyOpenInvitesAreListedNewestFirst() {
    // Given
    var older = save("older", NOW.minus(Duration.ofDays(3)));
    var newer = save("newer", NOW.minus(Duration.ofDays(1)));
    save("expired", NOW.minus(Duration.ofDays(14)));
    save("used", NOW.minus(Duration.ofDays(2))).accept(bea, NOW);

    // When
    var open = service.findOpen();

    // Then
    assertThat(open).extracting(Invite::getCode).containsExactly(newer.getCode(), older.getCode());
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
  }

  @Test
  void anOpenInviteIsFoundByItsCode() {
    // Given
    var invite = save("open", NOW);

    // When / Then
    assertThat(service.getOpen("open").getId()).isEqualTo(invite.getId());
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

  private Invite save(String code, Instant createdAt) {
    return repository.save(new Invite(code, ana, createdAt));
  }
}
