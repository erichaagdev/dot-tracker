package dev.erichaag.dottracker.security;

import static org.springframework.web.context.request.RequestAttributes.SCOPE_SESSION;

import dev.erichaag.dottracker.member.MemberService;
import dev.erichaag.dottracker.member.MemberService.GoogleAccount;
import dev.erichaag.dottracker.member.MemberService.GoogleSignIn.NotInvited;
import dev.erichaag.dottracker.member.MemberService.GoogleSignIn.SignedIn;
import dev.erichaag.dottracker.member.MemberService.GoogleSignIn.WrongAccount;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Lets a Google account in only when it belongs to a member, is an admin email or comes with an open invite for its
 * email, which {@link JoinController} keeps in the session. Anyone else is refused with an
 * {@link OAuth2AuthenticationException} whose error code {@link GoogleSignInFailureHandler} reads, {@value #WRONG_ACCOUNT}
 * for an invite sent to a different email and {@value #NOT_INVITED} otherwise. A refused sign-in leaves the invite in
 * the session, so the person can try again with another Google account. The signed-in user is named after their
 * member id and carries their role.
 */
@Service
@Profile("!demo")
public class MemberOidcUserService extends OidcUserService {

  static final String NOT_INVITED = "not_invited";

  static final String WRONG_ACCOUNT = "wrong_account";

  private final MemberService members;

  public MemberOidcUserService(MemberService members) {
    this.members = members;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
    var user = super.loadUser(request);
    var email = user.getEmail();
    if (email == null) {
      throw notInvited();
    }
    var account = new GoogleAccount(Objects.requireNonNull(user.getSubject()), email,
      Boolean.TRUE.equals(user.getEmailVerified()), user.getFullName());
    var attributes = RequestContextHolder.currentRequestAttributes();
    var inviteCode = (String) attributes.getAttribute(JoinController.INVITE_CODE, SCOPE_SESSION);
    var member = switch (members.signInWithGoogle(account, inviteCode)) {
      case SignedIn(var signedIn) -> signedIn;
      case NotInvited() -> throw notInvited();
      case WrongAccount() -> throw new OAuth2AuthenticationException(
        new OAuth2Error(WRONG_ACCOUNT, "This Google account's email isn't the one the invite was sent to.", null));
    };
    attributes.removeAttribute(JoinController.INVITE_CODE, SCOPE_SESSION);
    return new MemberOidcUser(Objects.requireNonNull(member.getId()), member.getRole(), user.getIdToken(),
      user.getUserInfo());
  }

  private static OAuth2AuthenticationException notInvited() {
    return new OAuth2AuthenticationException(
      new OAuth2Error(NOT_INVITED, "This Google account isn't in the group and has no open invite.", null));
  }
}
