package dev.erichaag.dottracker.invite;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.erichaag.dottracker.invite.InviteService.OpenInvite;
import dev.erichaag.dottracker.security.CurrentMember;
import dev.erichaag.dottracker.security.SecurityConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(InviteRestController.class)
@Import({SecurityConfiguration.class, CurrentMember.class})
class InviteRestControllerTest {

  private static final UUID ANA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3d");

  private static final UUID BEA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3e");

  private static final UUID INVITE = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3f");

  private static final String KIM = """
    {"email": "kim@example.com"}
    """;

  @Autowired
  MockMvc mvc;

  @MockitoBean
  InviteService service;

  @Test
  void openInvitesComeBackWithWhoTheyAreForWhoMadeThemAndTheirLinks() throws Exception {
    // Given
    when(service.findOpen()).thenReturn(List.of(invite("Zm9vYmFyYmF6cXV4cXV1eA")));

    // When / Then
    mvc.perform(get("/api/invites").with(admin()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value(INVITE.toString()))
      .andExpect(jsonPath("$[0].email").value("kim@example.com"))
      .andExpect(jsonPath("$[0].link").value("http://localhost/join/Zm9vYmFyYmF6cXV4cXV1eA"))
      .andExpect(jsonPath("$[0].createdBy").value(ANA.toString()))
      .andExpect(jsonPath("$[0].createdByName").value("Ana"))
      .andExpect(jsonPath("$[0].createdAt").value("2026-10-25T17:00:00Z"))
      .andExpect(jsonPath("$[0].expiresAt").value("2026-11-08T17:00:00Z"));
  }

  @Test
  void creatingAnInviteForAnEmailAnswersWithItsLinkAtTheAddressTheAdminUsed() throws Exception {
    // Given
    when(service.create("kim@example.com", ANA)).thenReturn(invite("Zm9vYmFyYmF6cXV4cXV1eA"));

    // When / Then
    mvc.perform(withBody(post("https://dots.example.com:443/api/invites"), KIM).with(admin()).with(csrf()))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").value(INVITE.toString()))
      .andExpect(jsonPath("$.email").value("kim@example.com"))
      .andExpect(jsonPath("$.link").value("https://dots.example.com/join/Zm9vYmFyYmF6cXV4cXV1eA"))
      .andExpect(jsonPath("$.createdBy").value(ANA.toString()))
      .andExpect(jsonPath("$.createdByName").value("Ana"))
      .andExpect(jsonPath("$.createdAt").value("2026-10-25T17:00:00Z"))
      .andExpect(jsonPath("$.expiresAt").value("2026-11-08T17:00:00Z"));
    verify(service).create("kim@example.com", ANA);
  }

  @Test
  void aRefusedInviteAnswers400WithTheReason() throws Exception {
    // Given
    when(service.create("bea@example.com", ANA))
      .thenThrow(new IllegalArgumentException("bea@example.com is already in the group."));

    // When / Then
    mvc.perform(withBody(post("/api/invites"), """
        {"email": "bea@example.com"}
        """).with(admin()).with(csrf()))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("bea@example.com is already in the group."));
  }

  @Test
  void anInviteWithoutAnEmailIsRefused() throws Exception {
    // When / Then
    mvc.perform(withBody(post("/api/invites"), "{}").with(admin()).with(csrf()))
      .andExpect(status().isBadRequest());
    mvc.perform(post("/api/invites").with(admin()).with(csrf()))
      .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void creatingAnInviteNeedsTheCsrfToken() throws Exception {
    // When / Then
    mvc.perform(withBody(post("/api/invites"), KIM).with(admin())).andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void revokingAnInviteAnswers204() throws Exception {
    // When / Then
    mvc.perform(delete("/api/invites/{id}", INVITE).with(admin()).with(csrf()))
      .andExpect(status().isNoContent());
    verify(service).delete(INVITE);
  }

  @Test
  void revokingAnInviteNeedsTheCsrfToken() throws Exception {
    // When / Then
    mvc.perform(delete("/api/invites/{id}", INVITE).with(admin())).andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void aMemberCannotSeeCreateOrRevokeInvites() throws Exception {
    // When / Then
    mvc.perform(get("/api/invites").with(member()))
      .andExpect(status().isForbidden())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("Only an admin can do that."));
    mvc.perform(withBody(post("/api/invites"), KIM).with(member()).with(csrf()))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.detail").value("Only an admin can do that."));
    mvc.perform(delete("/api/invites/{id}", INVITE).with(member()).with(csrf()))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.detail").value("Only an admin can do that."));
    verifyNoInteractions(service);
  }

  @Test
  void withoutASessionInvitesAnswer401() throws Exception {
    // When / Then
    mvc.perform(get("/api/invites")).andExpect(status().isUnauthorized());
    mvc.perform(withBody(post("/api/invites"), KIM).with(csrf())).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static RequestPostProcessor admin() {
    return oidcLogin()
      .idToken(token -> token.subject(ANA.toString()))
      .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private static RequestPostProcessor member() {
    return oidcLogin()
      .idToken(token -> token.subject(BEA.toString()))
      .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
  }

  private static OpenInvite invite(String code) {
    var invite = new Invite(code, "kim@example.com", ANA, Instant.parse("2026-10-25T17:00:00Z"));
    ReflectionTestUtils.setField(invite, "id", INVITE);
    return new OpenInvite(invite, "Ana");
  }
}
