package dev.erichaag.dottracker.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InviteTest {

  private static final Instant CREATED_AT = Instant.parse("2026-10-25T17:00:00Z");

  private static final UUID ANA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3d");

  private static final UUID BEA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3e");

  @Test
  void anInviteExpires14DaysAfterItWasMade() {
    // When
    var invite = invite();

    // Then
    assertThat(invite.getExpiresAt()).isEqualTo(Instant.parse("2026-11-08T17:00:00Z"));
  }

  @Test
  void anInviteIsOpenUntilItExpires() {
    // Given
    var invite = invite();

    // When / Then
    assertThat(invite.isOpen(invite.getExpiresAt().minus(Duration.ofSeconds(1)))).isTrue();
    assertThat(invite.isOpen(invite.getExpiresAt())).isFalse();
  }

  @Test
  void acceptingAnInviteRecordsWhoUsedItAndClosesIt() {
    // Given
    var invite = invite();
    var acceptedAt = CREATED_AT.plus(Duration.ofDays(1));

    // When
    invite.accept(BEA, acceptedAt);

    // Then
    assertThat(invite.getAcceptedBy()).isEqualTo(BEA);
    assertThat(invite.getAcceptedAt()).isEqualTo(acceptedAt);
    assertThat(invite.isOpen(acceptedAt)).isFalse();
  }

  @Test
  void aUsedInviteCannotBeAcceptedAgain() {
    // Given
    var invite = invite();
    invite.accept(BEA, CREATED_AT);

    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> invite.accept(ANA, CREATED_AT))
      .withMessage("This invite has expired or was already used. Ask for a new one.");
    assertThat(invite.getAcceptedBy()).isEqualTo(BEA);
  }

  @Test
  void anExpiredInviteCannotBeAccepted() {
    // Given
    var invite = invite();

    // When / Then
    assertThatIllegalArgumentException().isThrownBy(() -> invite.accept(BEA, invite.getExpiresAt()));
    assertThat(invite.getAcceptedBy()).isNull();
  }

  private static Invite invite() {
    return new Invite("code", ANA, CREATED_AT);
  }
}
