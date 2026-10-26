package dev.erichaag.dottracker.invite;

import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.MemberRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteService {

  private static final Logger logger = LoggerFactory.getLogger(InviteService.class);

  private static final int CODE_BYTES = 16;

  private final InviteRepository repository;

  // MemberService already depends on this service to sign people in with an invite, so members are read through their
  // repository rather than MemberService, which would make the two services depend on each other
  private final MemberRepository members;

  private final Clock clock;

  private final SecureRandom random = new SecureRandom();

  public InviteService(InviteRepository repository, MemberRepository members, Clock clock) {
    this.repository = repository;
    this.members = members;
    this.clock = clock;
  }

  /** The invites nobody has used yet that haven't expired, newest first, each with who made it, in two queries. */
  @Transactional(readOnly = true)
  public List<OpenInvite> findOpen() {
    return withCreators(repository.findByAcceptedByIsNullAndExpiresAtAfterOrderByCreatedAtDesc(clock.instant()));
  }

  /**
   * Makes an invite for a friend's email address. An address that isn't one, that belongs to someone already in the
   * group or that already has an open invite is refused.
   */
  @Transactional
  public OpenInvite create(String email, UUID createdBy) {
    var bytes = new byte[CODE_BYTES];
    random.nextBytes(bytes);
    var code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var invite = new Invite(code, email, createdBy, clock.instant());
    if (members.findByEmail(invite.getEmail()).isPresent()) {
      throw new IllegalArgumentException(invite.getEmail() + " is already in the group.");
    }
    if (repository.existsByEmailAndAcceptedByIsNullAndExpiresAtAfter(invite.getEmail(), clock.instant())) {
      throw new IllegalArgumentException(
        invite.getEmail() + " already has an open invite. Copy or share its link from the list below.");
    }
    repository.save(invite);
    logger.info("Made an invite for {}", invite.getEmail());
    return withCreators(List.of(invite)).getFirst();
  }

  /** Revokes an invite. One that's already gone is left gone, so a retried request does no harm. */
  @Transactional
  public void delete(UUID id) {
    repository.deleteById(id);
  }

  /** The open invite with this code, if there is one. */
  @Transactional(readOnly = true)
  public Optional<Invite> findOpen(String code) {
    return repository.findByCode(code).filter(invite -> invite.isOpen(clock.instant()));
  }

  /** The open invite with this code, for someone who might not hold a code at all. */
  @Transactional(readOnly = true)
  public Invite getOpen(@Nullable String code) {
    return Optional.ofNullable(code)
      .flatMap(this::findOpen)
      .orElseThrow(() -> new NoSuchElementException("This invite has expired or was already used. Ask for a new one."));
  }

  @Transactional(readOnly = true)
  public boolean isOpen(String code) {
    return findOpen(code).isPresent();
  }

  @Transactional
  public void accept(String code, UUID memberId) {
    var invite = repository.findByCode(code)
      .orElseThrow(() -> new NoSuchElementException("That invite link doesn't work. Ask for a new one."));
    invite.accept(memberId, clock.instant());
  }

  // Every invite's creator is a member, since the foreign key keeps a member who made an invite from being deleted
  private List<OpenInvite> withCreators(List<Invite> invites) {
    var creatorIds = invites.stream().map(Invite::getCreatedBy).distinct().toList();
    var names = members.findAllById(creatorIds).stream()
      .collect(Collectors.toMap(member -> Objects.requireNonNull(member.getId()), Member::getDisplayName));
    return invites.stream()
      .map(invite -> new OpenInvite(invite, Objects.requireNonNull(names.get(invite.getCreatedBy()))))
      .toList();
  }

  /** An open invite with the display name of the admin who made it. */
  public record OpenInvite(Invite invite, String createdByName) {
  }
}
