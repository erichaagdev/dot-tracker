package dev.erichaag.dottracker.member;

import static org.hibernate.annotations.UuidGenerator.Style.VERSION_7;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * Someone in the group. The email is kept trimmed and in lowercase, the display name is trimmed and never blank, and
 * once a Google account is linked it stays the same.
 */
@Entity
public class Member {

  @Id
  @UuidGenerator(style = VERSION_7)
  private @Nullable UUID id;

  private @Nullable String googleSubject;

  private String email = "";

  private String displayName = "";

  @Enumerated(EnumType.STRING)
  private Role role = Role.MEMBER;

  private Instant joinedAt = Instant.EPOCH;

  @Version
  private @Nullable Long version;

  protected Member() {
  }

  public Member(String email, String displayName, Role role, Instant joinedAt) {
    this.email = normalizeEmail(email);
    if (this.email.isEmpty()) {
      throw new IllegalArgumentException("A member needs an email address.");
    }
    this.displayName = displayName.strip();
    if (this.displayName.isEmpty()) {
      throw new IllegalArgumentException("A member needs a name.");
    }
    this.role = role;
    this.joinedAt = joinedAt;
  }

  /** The form an email is stored and compared in, so the same address matches however it was typed. */
  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  public @Nullable UUID getId() {
    return id;
  }

  public @Nullable String getGoogleSubject() {
    return googleSubject;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public Role getRole() {
    return role;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public void linkGoogleAccount(String subject) {
    if (googleSubject != null && !googleSubject.equals(subject)) {
      throw new IllegalArgumentException("This member already signs in with a different Google account.");
    }
    googleSubject = subject;
  }

  public void changeRole(Role role) {
    this.role = role;
  }

  public enum Role {
    ADMIN,
    MEMBER
  }
}
