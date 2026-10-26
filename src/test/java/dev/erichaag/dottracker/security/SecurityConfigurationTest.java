package dev.erichaag.dottracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.member.MemberService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// These tests send the CSRF token the way the frontend does, from the XSRF-TOKEN cookie into the X-XSRF-TOKEN header.
// spring-security-test's csrf() would swap the context's token repository for one that sets no cookie.
@WebMvcTest(SecurityConfigurationTest.ProbeController.class)
@Import({SecurityConfiguration.class, SecurityConfigurationTest.ProbeController.class})
class SecurityConfigurationTest {

  private static final UUID ANA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3d");

  @Autowired
  MockMvc mvc;

  @Test
  void anApiRequestWithoutASessionAnswers401RatherThanARedirect() throws Exception {
    // When / Then
    mvc.perform(get("/api/probe").accept(MediaType.TEXT_HTML))
      .andExpect(status().isUnauthorized())
      .andExpect(header().doesNotExist("Location"));
  }

  @Test
  void aSignedInApiRequestGoesThrough() throws Exception {
    // When / Then
    mvc.perform(get("/api/probe").with(oidcLogin())).andExpect(status().isOk());
  }

  @Test
  void theCsrfTokenComesInACookieTheFrontendCanRead() throws Exception {
    // When / Then
    mvc.perform(get("/"))
      .andExpect(cookie().exists("XSRF-TOKEN"))
      .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
  }

  @Test
  void anApiWriteGoesThroughWithTheCookiesTokenInTheHeader() throws Exception {
    // Given
    var token = xsrfToken();

    // When / Then
    mvc.perform(post("/api/probe").with(oidcLogin()).cookie(token).header("X-XSRF-TOKEN", token.getValue()))
      .andExpect(status().isOk());
  }

  @Test
  void anApiWriteWithoutTheCsrfTokenIsRefused() throws Exception {
    // Given
    var token = xsrfToken();

    // When / Then
    mvc.perform(post("/api/probe").with(oidcLogin()).cookie(token)).andExpect(status().isForbidden());
  }

  @Test
  void signingOutEndsTheSessionAndAnswers204() throws Exception {
    // Given
    var token = xsrfToken();
    var session = new MockHttpSession();

    // When / Then
    mvc.perform(post("/api/logout")
        .with(oidcLogin())
        .session(session)
        .cookie(token)
        .header("X-XSRF-TOKEN", token.getValue()))
      .andExpect(status().isNoContent())
      .andExpect(unauthenticated());
    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  void signingOutNeedsTheCsrfToken() throws Exception {
    // When / Then
    mvc.perform(post("/api/logout").with(oidcLogin())).andExpect(status().isForbidden());
  }

  @Test
  void aRefusedGoogleSignInShowsTheNotInvitedMessage() throws Exception {
    // When / Then
    mvc.perform(get("/login/oauth2/code/google").param("code", "code").param("state", "state"))
      .andExpect(redirectedUrl("/?signin=not-invited"));
  }

  @Test
  void signingInWithGoogleAlwaysShowsGooglesAccountChooser() throws Exception {
    // When / Then
    mvc.perform(get("/oauth2/authorization/google"))
      .andExpect(status().isFound())
      .andExpect(header().string("Location", startsWith("https://accounts.google.com/")))
      .andExpect(header().string("Location", containsString("prompt=select_account")));
  }

  private Cookie xsrfToken() throws Exception {
    return Objects.requireNonNull(mvc.perform(get("/")).andReturn().getResponse().getCookie("XSRF-TOKEN"));
  }

  @Nested
  @ActiveProfiles("demo")
  class DemoProfile {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MemberService members;

    @Test
    void aMemberSignsInWithTheirEmailAndThePasswordDemoAsTheirMemberId() throws Exception {
      // Given
      when(members.findByEmail("ana@example.com")).thenReturn(Optional.of(ana()));

      // When / Then
      mvc.perform(formLogin().user("ana@example.com").password("demo"))
        .andExpect(redirectedUrl("/"))
        .andExpect(authenticated().withAuthenticationName(ANA.toString()).withRoles("ADMIN"));
    }

    @Test
    void aWrongPasswordIsRefused() throws Exception {
      // Given
      when(members.findByEmail("ana@example.com")).thenReturn(Optional.of(ana()));

      // When / Then
      mvc.perform(formLogin().user("ana@example.com").password("wrong"))
        .andExpect(redirectedUrl("/login?error"))
        .andExpect(unauthenticated());
    }

    @Test
    void theLoginPageIsSpringSecuritysOwn() throws Exception {
      // When / Then
      mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void anApiRequestWithoutASessionStillAnswers401() throws Exception {
      // When / Then
      mvc.perform(get("/api/probe").accept(MediaType.TEXT_HTML))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("Location"));
    }

    private static Member ana() {
      var ana = new Member("ana@example.com", "Ana", Role.ADMIN, Instant.EPOCH);
      ReflectionTestUtils.setField(ana, "id", ANA);
      return ana;
    }
  }

  @RestController
  static class ProbeController {

    @GetMapping("/api/probe")
    String read() {
      return "read";
    }

    @PostMapping("/api/probe")
    String write() {
      return "written";
    }
  }
}
