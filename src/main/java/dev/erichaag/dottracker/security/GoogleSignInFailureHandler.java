package dev.erichaag.dottracker.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Sends a failed Google sign-in to the sign-in screen with the reason it explains. An invite used with a Google account
 * for a different email lands on {@code /?signin=wrong-account}, and every other failure on
 * {@code /?signin=not-invited}.
 */
final class GoogleSignInFailureHandler implements AuthenticationFailureHandler {

  private final RedirectStrategy redirects = new DefaultRedirectStrategy();

  @Override
  public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
    AuthenticationException exception) throws IOException {
    var wrongAccount = exception instanceof OAuth2AuthenticationException oauth2
      && MemberOidcUserService.WRONG_ACCOUNT.equals(oauth2.getError().getErrorCode());
    redirects.sendRedirect(request, response, wrongAccount ? "/?signin=wrong-account" : "/?signin=not-invited");
  }
}
