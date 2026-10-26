package dev.erichaag.dottracker.security;

import dev.erichaag.dottracker.invite.Invite;
import dev.erichaag.dottracker.invite.InviteService;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * Starts sign-in from an invite link. An open invite's code waits in the session for {@link MemberOidcUserService},
 * and the person lands on the frontend's welcome screen, which reads who the invite is for and when it expires and
 * links on to Google. Any other code sends them to the sign-in screen's message about an expired invite.
 */
@Controller
public class JoinController {

  static final String INVITE_CODE = "dot-tracker.invite-code";

  private final InviteService invites;

  public JoinController(InviteService invites) {
    this.invites = invites;
  }

  @GetMapping("/join/{code}")
  public String join(@PathVariable String code, HttpSession session) {
    if (!invites.isOpen(code)) {
      return "redirect:/?signin=invite-expired";
    }
    session.setAttribute(INVITE_CODE, code);
    return "redirect:/welcome";
  }

  /** The invite held in the session, which the welcome screen reads before anyone has signed in. */
  @GetMapping("/api/invites/pending")
  @ResponseBody
  public PendingInviteResponse pending(@SessionAttribute(name = INVITE_CODE, required = false) @Nullable String code) {
    return PendingInviteResponse.from(invites.getOpen(code));
  }

  /** The invite's email address, so the welcome screen can say which Google account to continue with. */
  public record PendingInviteResponse(String email, Instant expiresAt) {

    static PendingInviteResponse from(Invite invite) {
      return new PendingInviteResponse(invite.getEmail(), invite.getExpiresAt());
    }
  }
}
