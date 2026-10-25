package dev.erichaag.dottracker.invite;

import static org.hibernate.annotations.UuidGenerator.Style.VERSION_7;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/** A link an admin sends to a friend. It works once, and only until it expires 14 days after it was made. */
@Entity
public class Invite {

  public static final Duration LIFETIME = Duration.ofDays(14);

  @Id
  @UuidGenerator(style = VERSION_7)
  private @Nullable UUID id;

  private String code = "";

  private UUID createdBy = new UUID(0, 0);

  private Instant createdAt = Instant.EPOCH;

  private Instant expiresAt = Instant.EPOCH;

  private @Nullable UUID acceptedBy;

  private @Nullable Instant acceptedAt;

  @Version
  private @Nullable Long version;

  protected Invite() {
  }

  public Invite(String code, UUID createdBy, Instant createdAt) {
    this.code = code;
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

  public void accept(UUID memberId, Instant now) {
    if (!isOpen(now)) {
      throw new IllegalArgumentException("This invite has expired or was already used. Ask for a new one.");
    }
    acceptedBy = memberId;
    acceptedAt = now;
  }
}
