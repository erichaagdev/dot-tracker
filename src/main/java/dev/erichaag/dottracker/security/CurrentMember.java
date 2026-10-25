package dev.erichaag.dottracker.security;

import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The signed-in member's id, read from the security context. Google sign-in and the demo profile's form login both
 * name the signed-in person after their member id, so this reads either the same way.
 */
@Component
public class CurrentMember {

  public UUID id() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
      throw new AuthenticationCredentialsNotFoundException("Nobody is signed in");
    }
    return UUID.fromString(authentication.getName());
  }
}
