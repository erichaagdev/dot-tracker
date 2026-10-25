package dev.erichaag.dottracker.invite;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteService {

  private static final int CODE_BYTES = 16;

  private final InviteRepository repository;

  private final Clock clock;

  private final SecureRandom random = new SecureRandom();

  public InviteService(InviteRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /** The invites nobody has used yet that haven't expired, newest first. */
  @Transactional(readOnly = true)
  public List<Invite> findOpen() {
    return repository.findByAcceptedByIsNullAndExpiresAtAfterOrderByCreatedAtDesc(clock.instant());
  }

  @Transactional
  public Invite create(UUID createdBy) {
    var bytes = new byte[CODE_BYTES];
    random.nextBytes(bytes);
    var code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return repository.save(new Invite(code, createdBy, clock.instant()));
  }

  /** Revokes an invite. One that's already gone is left gone, so a retried request does no harm. */
  @Transactional
  public void delete(UUID id) {
    repository.deleteById(id);
  }

  /** The open invite with this code, for someone who might not hold a code at all. */
  @Transactional(readOnly = true)
  public Invite getOpen(@Nullable String code) {
    return Optional.ofNullable(code)
      .flatMap(repository::findByCode)
      .filter(invite -> invite.isOpen(clock.instant()))
      .orElseThrow(() -> new NoSuchElementException("This invite has expired or was already used. Ask for a new one."));
  }

  @Transactional(readOnly = true)
  public boolean isOpen(String code) {
    return repository.findByCode(code)
      .map(invite -> invite.isOpen(clock.instant()))
      .orElse(false);
  }

  @Transactional
  public void accept(String code, UUID memberId) {
    var invite = repository.findByCode(code)
      .orElseThrow(() -> new NoSuchElementException("That invite link doesn't work. Ask for a new one."));
    invite.accept(memberId, clock.instant());
  }
}
