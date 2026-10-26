package dev.erichaag.dottracker.invite;

import static org.hibernate.annotations.UuidGenerator.Style.VERSION_7;

import dev.erichaag.dottracker.member.Member;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A link an admin sends to a friend at one email address. It works once, only for that address, and only until it
 * expires 14 days after it was made. The email is kept the way {@link Member#normalizeEmail} gives it.
 */
@Entity
public class Invite {

  public static final Duration LIFETIME = Duration.ofDays(14);

  // Something before and after a single @, with a dot in the part after it, which catches typos without refusing any
  // address a friend might really have
  private static final Pattern EMAIL_ADDRESS = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

  @Id
  @UuidGenerator(style = VERSION_7)
  private @Nullable UUID id;

  private String code = "";

  private String email = "";

  private UUID createdBy = new UUID(0, 0);

  private Instant createdAt = Instant.EPOCH;

  private Instant expiresAt = Instant.EPOCH;

  private @Nullable UUID acceptedBy;

  private @Nullable Instant acceptedAt;

  @Version
  private @Nullable Long version;

  protected Invite() {
  }

  public Invite(String code, String email, UUID createdBy, Instant createdAt) {
    this.code = code;
    this.email = Member.normalizeEmail(email);
    if (this.email.isEmpty()) {
      throw new IllegalArgumentException("Enter the email address of the friend you're inviting.");
    }
    if (!EMAIL_ADDRESS.matcher(this.email).matches()) {
      throw new IllegalArgumentException(email.strip() + " isn't an email address. Check it for a typo and try again.");
    }
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.expiresAt = createdAt.plus(LIFETIME);
  }

  public @Nullable UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getEmail() {
    return email;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public @Nullable UUID getAcceptedBy() {
    return acceptedBy;
  }

  public @Nullable Instant getAcceptedAt() {
    return acceptedAt;
  }

  /** Whether the invite can still be used, which is when nobody has used it and it hasn't expired. */
  public boolean isOpen(Instant now) {
    return acceptedBy == null && now.isBefore(expiresAt);
  }

  /** Whether the invite was sent to this email address, however it's typed. */
  public boolean isFor(String email) {
    return this.email.equals(Member.normalizeEmail(email));
  }

  public void accept(UUID memberId, Instant now) {
    if (!isOpen(now)) {
      throw new IllegalArgumentException("This invite has expired or was already used. Ask for a new one.");
    }
    acceptedBy = memberId;
    acceptedAt = now;
  }
}
