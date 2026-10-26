package dev.erichaag.dottracker.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

// SecurityConfigurationTest checks that a failure through the filter chain lands here, on the not-invited message
class GoogleSignInFailureHandlerTest {

  private final GoogleSignInFailureHandler handler = new GoogleSignInFailureHandler();

  @Test
  void anInviteUsedWithAGoogleAccountForAnotherEmailShowsTheWrongAccountMessage() throws Exception {
    // Given
    var response = new MockHttpServletResponse();

    // When
    handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
      new OAuth2AuthenticationException(new OAuth2Error(MemberOidcUserService.WRONG_ACCOUNT)));

    // Then
    assertThat(response.getRedirectedUrl()).isEqualTo("/?signin=wrong-account");
  }

  @ParameterizedTest
  @MethodSource("otherFailures")
  void everyOtherFailureShowsTheNotInvitedMessage(AuthenticationException failure) throws Exception {
    // Given
    var response = new MockHttpServletResponse();

    // When
    handler.onAuthenticationFailure(new MockHttpServletRequest(), response, failure);

    // Then
    assertThat(response.getRedirectedUrl()).isEqualTo("/?signin=not-invited");
  }

  static Stream<AuthenticationException> otherFailures() {
    return Stream.of(
      new OAuth2AuthenticationException(new OAuth2Error(MemberOidcUserService.NOT_INVITED)),
      new OAuth2AuthenticationException(new OAuth2Error("authorization_request_not_found")),
      new BadCredentialsException("Google said no")
    );
  }
}
