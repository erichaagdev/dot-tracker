package dev.erichaag.dottracker.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.erichaag.dottracker.member.Member.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MemberTest {

  @Test
  void theEmailIsKeptTrimmedAndInLowercase() {
    // When
    var member = new Member("  Ana@Example.COM ", "Ana", Role.MEMBER, Instant.EPOCH);

    // Then
    assertThat(member.getEmail()).isEqualTo("ana@example.com");
  }

  @Test
  void theDisplayNameIsTrimmed() {
    // When
    var member = new Member("ana@example.com", "  Ana Torres ", Role.MEMBER, Instant.EPOCH);

    // Then
    assertThat(member.getDisplayName()).isEqualTo("Ana Torres");
  }

  @Test
  void aBlankDisplayNameIsRefused() {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> new Member("ana@example.com", "  ", Role.MEMBER, Instant.EPOCH))
      .withMessage("A member needs a name.");
  }

  @Test
  void aBlankEmailIsRefused() {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> new Member(" ", "Ana", Role.MEMBER, Instant.EPOCH))
      .withMessage("A member needs an email address.");
  }

  @Test
  void linkingTheSameGoogleAccountAgainChangesNothing() {
    // Given
    var member = member();
    member.linkGoogleAccount("google-ana");

    // When
    member.linkGoogleAccount("google-ana");

    // Then
    assertThat(member.getGoogleSubject()).isEqualTo("google-ana");
  }

  @Test
  void aLinkedGoogleAccountCannotBeSwappedForAnother() {
    // Given
    var member = member();
    member.linkGoogleAccount("google-ana");

    // When / Then
    assertThatIllegalArgumentException().isThrownBy(() -> member.linkGoogleAccount("google-someone-else"));
    assertThat(member.getGoogleSubject()).isEqualTo("google-ana");
  }

  private static Member member() {
    return new Member("ana@example.com", "Ana", Role.MEMBER, Instant.EPOCH);
  }
}
