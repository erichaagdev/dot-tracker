package dev.erichaag.dottracker.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import dev.erichaag.dottracker.member.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DemoSignInController.class)
@Import(SecurityConfiguration.class)
@ActiveProfiles("demo")
class DemoSignInControllerTest {

  @Autowired
  MockMvc mvc;

  @MockitoBean
  MemberService members;

  @Test
  void continuingWithGoogleInTheDemoLandsOnTheFormLogin() throws Exception {
    // When / Then
    mvc.perform(get("/oauth2/authorization/google")).andExpect(redirectedUrl("/login"));
  }
}
