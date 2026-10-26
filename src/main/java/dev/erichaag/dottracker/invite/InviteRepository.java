package dev.erichaag.dottracker.invite;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

  Optional<Invite> findByCode(String code);

  List<Invite> findByAcceptedByIsNullAndExpiresAtAfterOrderByCreatedAtDesc(Instant now);

  /** Whether an email, already in the form {@code Member.normalizeEmail} gives, has an open invite. */
  boolean existsByEmailAndAcceptedByIsNullAndExpiresAtAfter(String email, Instant now);
}
