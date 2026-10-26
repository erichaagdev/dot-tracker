package dev.erichaag.dottracker.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
  void anInvitesEmailIsTrimmedAndInLowercase() {
    // When
    var invite = new Invite("code", "  Kim.Lee@Example.com ", ANA, CREATED_AT);

    // Then
    assertThat(invite.getEmail()).isEqualTo("kim.lee@example.com");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void anInviteNeedsAnEmail(String email) {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> new Invite("code", email, ANA, CREATED_AT))
      .withMessage("Enter the email address of the friend you're inviting.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"kim", "kim@example", "@example.com", "kim@@example.com", "kim lee@example.com", "kim@example."})
  void anInviteRefusesSomethingThatIsNotAnEmailAddress(String email) {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> new Invite("code", " " + email + " ", ANA, CREATED_AT))
      .withMessage(email + " isn't an email address. Check it for a typo and try again.");
  }

  @Test
  void anInviteIsForItsEmailHoweverItIsTyped() {
    // Given
    var invite = invite();

    // When / Then
    assertThat(invite.isFor(" Kim@Example.com")).isTrue();
    assertThat(invite.isFor("kim@example.org")).isFalse();
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
    return new Invite("code", "kim@example.com", ANA, CREATED_AT);
  }
}
