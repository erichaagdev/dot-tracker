package dev.erichaag.dottracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

import dev.erichaag.dottracker.security.SecurityConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SpaForwardingController.class)
@Import(SecurityConfiguration.class)
class SpaForwardingControllerTest {

  @Autowired
  MockMvc mvc;

  @ParameterizedTest
  @ValueSource(strings = {"/shelf", "/welcome", "/books/abc", "/books/0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3d/edit"})
  void aPathTheFrontendRoutesForwardsToTheApp(String path) throws Exception {
    // When / Then
    mvc.perform(get(path)).andExpect(forwardedUrl("/index.html"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "/api/books",
    "/api/books/abc",
    "/actuator/health",
    "/oauth2/authorization/google",
    "/login",
    "/join/abc",
    "/favicon.svg",
    "/assets/app.js",
    "/books/abc/cover.png",
  })
  void aServerPathOrAFileIsNotForwarded(String path) throws Exception {
    // When / Then
    mvc.perform(get(path)).andExpect(forwardedUrl(null));
  }
}
