package dev.erichaag.dottracker.security;

import dev.erichaag.dottracker.member.Member.Role;
import java.io.Serial;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/** A member signed in with Google, named after their member id and holding their role as {@code ROLE_<role>}. */
final class MemberOidcUser extends DefaultOidcUser {

  @Serial
  private static final long serialVersionUID = 1L;

  private final UUID memberId;

  MemberOidcUser(UUID memberId, Role role, OidcIdToken idToken, @Nullable OidcUserInfo userInfo) {
    super(List.of(new SimpleGrantedAuthority("ROLE_" + role.name())), idToken, userInfo);
    this.memberId = memberId;
  }

  @Override
  public String getName() {
    return memberId.toString();
  }
}
