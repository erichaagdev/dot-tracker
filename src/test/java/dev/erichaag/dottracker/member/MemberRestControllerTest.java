package dev.erichaag.dottracker.member;

import static dev.erichaag.dottracker.member.Member.Role.ADMIN;
import static dev.erichaag.dottracker.member.Member.Role.MEMBER;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.erichaag.dottracker.member.Member.Role;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(MemberRestController.class)
@Import({SecurityConfiguration.class, CurrentMember.class})
class MemberRestControllerTest {

  private static final UUID ANA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3d");

  private static final UUID BEA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3e");

  @Autowired
  MockMvc mvc;

  @MockitoBean
  MemberService service;

  @Test
  void theSignedInMemberComesBackWithTheirRole() throws Exception {
    // Given
    when(service.getById(ANA)).thenReturn(member(ANA, "ana@example.com", "Ana", ADMIN));

    // When / Then
    mvc.perform(get("/api/me").with(signedInAs(ANA, ADMIN)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(ANA.toString()))
      .andExpect(jsonPath("$.email").value("ana@example.com"))
      .andExpect(jsonPath("$.displayName").value("Ana"))
      .andExpect(jsonPath("$.role").value("ADMIN"))
      .andExpect(jsonPath("$.joinedAt").value("2026-10-01T15:30:00Z"));
  }

  @Test
  void withoutASessionMeAnswers401RatherThanARedirect() throws Exception {
    // When / Then
    mvc.perform(get("/api/me").accept(MediaType.TEXT_HTML))
      .andExpect(status().isUnauthorized())
      .andExpect(header().doesNotExist("Location"));
  }

  @Test
  void anAdminSeesEveryoneInTheGroup() throws Exception {
    // Given
    when(service.findAll()).thenReturn(List.of(
      member(ANA, "ana@example.com", "Ana", ADMIN),
      member(BEA, "bea@example.com", "Bea", MEMBER)
    ));

    // When / Then
    mvc.perform(get("/api/members").with(signedInAs(ANA, ADMIN)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value(ANA.toString()))
      .andExpect(jsonPath("$[1].id").value(BEA.toString()))
      .andExpect(jsonPath("$[1].email").value("bea@example.com"))
      .andExpect(jsonPath("$[1].displayName").value("Bea"))
      .andExpect(jsonPath("$[1].role").value("MEMBER"))
      .andExpect(jsonPath("$[1].joinedAt").value("2026-10-01T15:30:00Z"));
  }

  @Test
  void aMemberCannotListTheGroup() throws Exception {
    // When / Then
    mvc.perform(get("/api/members").with(signedInAs(BEA, MEMBER)))
      .andExpect(status().isForbidden())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("Only an admin can do that."));
    verifyNoInteractions(service);
  }

  @Test
  void withoutASessionTheGroupAnswers401() throws Exception {
    // When / Then
    mvc.perform(get("/api/members")).andExpect(status().isUnauthorized());
  }

  @Test
  void anAdminChangesSomeonesRole() throws Exception {
    // Given
    when(service.changeRole(BEA, ADMIN)).thenReturn(member(BEA, "bea@example.com", "Bea", ADMIN));

    // When / Then
    mvc.perform(put("/api/members/{id}/role", BEA)
        .with(signedInAs(ANA, ADMIN))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"role": "ADMIN"}
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(BEA.toString()))
      .andExpect(jsonPath("$.role").value("ADMIN"));
    verify(service).changeRole(BEA, ADMIN);
  }

  @Test
  void changingARoleNeedsTheCsrfToken() throws Exception {
    // When / Then
    mvc.perform(put("/api/members/{id}/role", BEA)
        .with(signedInAs(ANA, ADMIN))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"role": "ADMIN"}
          """))
      .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void aMemberCannotChangeRoles() throws Exception {
    // When / Then
    mvc.perform(put("/api/members/{id}/role", BEA)
        .with(signedInAs(BEA, MEMBER))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"role": "ADMIN"}
          """))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.detail").value("Only an admin can do that."));
    verifyNoInteractions(service);
  }

  @Test
  void demotingTheLastAdminAnswers400WithTheReason() throws Exception {
    // Given
    when(service.changeRole(ANA, MEMBER)).thenThrow(new IllegalArgumentException(
      "The group needs at least one admin, so make someone else an admin before changing this."));

    // When / Then
    mvc.perform(put("/api/members/{id}/role", ANA)
        .with(signedInAs(ANA, ADMIN))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"role": "MEMBER"}
          """))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.detail")
        .value("The group needs at least one admin, so make someone else an admin before changing this."));
  }

  @Test
  void aRoleChangeWithoutARoleIsRefused() throws Exception {
    // When / Then
    mvc.perform(put("/api/members/{id}/role", BEA)
        .with(signedInAs(ANA, ADMIN))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  private static RequestPostProcessor signedInAs(UUID id, Role role) {
    return oidcLogin()
      .idToken(token -> token.subject(id.toString()))
      .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  private static Member member(UUID id, String email, String name, Role role) {
    var member = new Member(email, name, role, Instant.parse("2026-10-01T15:30:00Z"));
    ReflectionTestUtils.setField(member, "id", id);
    return member;
  }
}
