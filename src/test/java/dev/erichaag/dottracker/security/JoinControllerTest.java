package dev.erichaag.dottracker.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.erichaag.dottracker.invite.Invite;
import dev.erichaag.dottracker.invite.InviteService;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JoinController.class)
@Import(SecurityConfiguration.class)
class JoinControllerTest {

  private static final String CODE = "Zm9vYmFyYmF6cXV4cXV1eA";

  @Autowired
  MockMvc mvc;

  @MockitoBean
  InviteService invites;

  @Test
  void anOpenInviteIsKeptInTheSessionAndShowsTheWelcomeScreen() throws Exception {
    // Given
    when(invites.isOpen(CODE)).thenReturn(true);

    // When / Then
    mvc.perform(get("/join/{code}", CODE))
      .andExpect(redirectedUrl("/welcome"))
      .andExpect(request().sessionAttribute(JoinController.INVITE_CODE, CODE));
  }

  @Test
  void anExpiredUsedOrUnknownInviteShowsTheExpiredInviteMessage() throws Exception {
    // Given
    when(invites.isOpen(CODE)).thenReturn(false);

    // When / Then
    mvc.perform(get("/join/{code}", CODE))
      .andExpect(redirectedUrl("/?signin=invite-expired"))
      .andExpect(request().sessionAttributeDoesNotExist(JoinController.INVITE_CODE));
  }

  @Test
  void theWelcomeScreenReadsWhoTheHeldInviteIsForAndWhenItExpiresWithoutSigningIn() throws Exception {
    // Given
    var session = new MockHttpSession();
    session.setAttribute(JoinController.INVITE_CODE, CODE);
    when(invites.getOpen(CODE))
      .thenReturn(new Invite(CODE, "kim@example.com", UUID.randomUUID(), Instant.parse("2026-10-25T17:00:00Z")));

    // When / Then
    mvc.perform(get("/api/invites/pending").session(session))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value("kim@example.com"))
      .andExpect(jsonPath("$.expiresAt").value("2026-11-08T17:00:00Z"));
  }

  @Test
  void withoutAnOpenInviteHeldThePendingInviteAnswers404WithTheReason() throws Exception {
    // Given
    when(invites.getOpen(null))
      .thenThrow(new NoSuchElementException("This invite has expired or was already used. Ask for a new one."));

    // When / Then
    mvc.perform(get("/api/invites/pending"))
      .andExpect(status().isNotFound())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("This invite has expired or was already used. Ask for a new one."))
      .andExpect(request().sessionAttributeDoesNotExist(JoinController.INVITE_CODE));
  }
}
