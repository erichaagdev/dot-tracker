package dev.erichaag.dottracker.member;

import static dev.erichaag.dottracker.member.Member.Role.ADMIN;
import static dev.erichaag.dottracker.member.Member.Role.MEMBER;

import dev.erichaag.dottracker.DotTrackerProperties;
import dev.erichaag.dottracker.invite.InviteService;
import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.member.MemberService.GoogleSignIn.NotInvited;
import dev.erichaag.dottracker.member.MemberService.GoogleSignIn.SignedIn;
import dev.erichaag.dottracker.member.MemberService.GoogleSignIn.WrongAccount;
import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

  private static final Logger logger = LoggerFactory.getLogger(MemberService.class);

  private final MemberRepository repository;

  private final InviteService invites;

  private final DotTrackerProperties properties;

  private final FindByIndexNameSessionRepository<? extends Session> sessions;

  private final Clock clock;

  public MemberService(MemberRepository repository, InviteService invites, DotTrackerProperties properties,
    FindByIndexNameSessionRepository<? extends Session> sessions, Clock clock) {
    this.repository = repository;
    this.invites = invites;
    this.properties = properties;
    this.sessions = sessions;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Member getById(UUID id) {
    return repository.findById(id).orElseThrow(() -> new NoSuchElementException("That person isn't in the group."));
  }

  @Transactional(readOnly = true)
  public List<Member> findAll() {
    return repository.findAllByOrderByDisplayNameAsc();
  }

  @Transactional(readOnly = true)
  public Optional<Member> findByEmail(String email) {
    return repository.findByEmail(Member.normalizeEmail(email));
  }

  /**
   * Makes someone an admin or a member, and signs them out everywhere when that changes their role, since each of
   * their sessions holds the role they signed in with. A demoted admin loses admin rights at once, and a promoted
   * member gets them by signing in again.
   */
  @Transactional
  public Member changeRole(UUID id, Role role) {
    var member = getById(id);
    if (member.getRole() == role) {
      return member;
    }
    if (member.getRole() == ADMIN && repository.findByRole(ADMIN).size() == 1) {
      throw new IllegalArgumentException(
        "The group needs at least one admin, so make someone else an admin before changing this.");
    }
    member.changeRole(role);
    sessions.findByPrincipalName(id.toString()).keySet().forEach(sessions::deleteById);
    logger.info("Changed the role of {} to {} and signed them out", member.getEmail(), role);
    return member;
  }

  /**
   * Finds or adds the member for a Google account, or says why the account isn't let in. A member is found by their
   * Google account, then by a verified email, which links the account to them. Otherwise a verified email in
   * {@code dot-tracker.admin-emails} joins as an admin, and anyone else joins with an open invite for their verified
   * email, which this uses up. An open invite for a different email is refused as the wrong account, so the person can
   * try again with the right one.
   */
  @Transactional
  public GoogleSignIn signInWithGoogle(GoogleAccount account, @Nullable String inviteCode) {
    var bySubject = repository.findByGoogleSubject(account.subject());
    if (bySubject.isPresent()) {
      return new SignedIn(bySubject.get());
    }
    var email = Member.normalizeEmail(account.email());
    var byEmail = repository.findByEmail(email);
    if (byEmail.isPresent()) {
      var member = byEmail.get();
      // An unverified email proves nothing, and a linked member already has their own Google account
      if (!account.emailVerified() || member.getGoogleSubject() != null) {
        logger.warn("Refused a Google sign-in for {}, whose email belongs to a member it can't be linked to", email);
        return new NotInvited();
      }
      member.linkGoogleAccount(account.subject());
      logger.info("Linked a Google account to {}", email);
      return new SignedIn(member);
    }
    if (account.emailVerified() && isAdminEmail(email)) {
      return new SignedIn(join(account, email, ADMIN));
    }
    var invite = Optional.ofNullable(inviteCode).flatMap(invites::findOpen);
    if (invite.isEmpty()) {
      logger.info("Refused a Google sign-in for {}, who has no open invite", email);
      return new NotInvited();
    }
    if (!invite.get().isFor(email)) {
      logger.info("Refused a Google sign-in for {} with the invite for {}", email, invite.get().getEmail());
      return new WrongAccount();
    }
    if (!account.emailVerified()) {
      logger.warn("Refused a Google sign-in for {}, whose email Google hasn't verified", email);
      return new NotInvited();
    }
    var member = join(account, email, MEMBER);
    invites.accept(invite.get().getCode(), Objects.requireNonNull(member.getId()));
    return new SignedIn(member);
  }

  private boolean isAdminEmail(String email) {
    return properties.adminEmails().stream().map(Member::normalizeEmail).anyMatch(email::equals);
  }

  private Member join(GoogleAccount account, String email, Role role) {
    var name = account.name();
    var member = new Member(email, name != null && !name.isBlank() ? name : email, role, clock.instant());
    member.linkGoogleAccount(account.subject());
    logger.info("{} joined the group as {}", email, role);
    return repository.save(member);
  }

  /**
   * What Google says about the person signing in. The name is Google's {@code name} claim, which can be missing, and
   * the email stands in for it then.
   */
  public record GoogleAccount(String subject, String email, boolean emailVerified, @Nullable String name) {
  }

  /** How a Google sign-in turned out, which is the member it signed in or why it was refused. */
  public sealed interface GoogleSignIn {

    record SignedIn(Member member) implements GoogleSignIn {
    }

    /** The account isn't a member's, isn't an admin email and has no open invite. */
    record NotInvited() implements GoogleSignIn {
    }

    /** The account comes with an open invite that was sent to a different email address. */
    record WrongAccount() implements GoogleSignIn {
    }
  }
}
