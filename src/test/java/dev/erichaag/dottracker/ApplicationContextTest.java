package dev.erichaag.dottracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.ResolvableType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class ApplicationContextTest {

  @Autowired
  JdbcClient jdbc;

  @Autowired
  MockMvc mvc;

  @Autowired
  SessionRepository<?> sessions;

  @Autowired
  ApplicationContext context;

  @Test
  void theMigrationsCreateEveryTable() {
    // When
    var tables = jdbc.sql("select table_name from information_schema.tables where table_schema = 'public'")
      .query(String.class)
      .list();

    // Then
    assertThat(tables).contains("member", "invite", "product_line", "book", "book_listing", "collection_entry",
      "spring_session", "spring_session_attributes");
  }

  @Test
  @Transactional
  void aRowInsertedWithoutAnIdGetsATimeOrderedUuid() {
    // When
    var id = jdbc.sql("""
        insert into product_line (name, default_page_count, sort_order, color, version)
        values ('Test line', 20, 99, 'DBL', 0)
        returning id
        """)
      .query(UUID.class)
      .single();

    // Then
    assertThat(id.version()).isEqualTo(7);
  }

  @Test
  void theHealthEndpointReportsUp() throws Exception {
    // When / Then
    mvc.perform(get("/actuator/health"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void anApiRequestWithoutASessionAnswers401() throws Exception {
    // When / Then
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void signInStartsAtGoogle() throws Exception {
    // When / Then
    mvc.perform(get("/oauth2/authorization/google"))
      .andExpect(status().isFound())
      .andExpect(header().string("Location", startsWith("https://accounts.google.com/")));
  }

  // oauth2Login uses the one bean of this type, and without it a plain OidcUserService would let any Google account in
  @Test
  void googleSignInGoesThroughTheInvitationRules() {
    // Given
    var type = ResolvableType.forClassWithGenerics(OAuth2UserService.class, OidcUserRequest.class, OidcUser.class);

    // When / Then
    assertThat(context.getBeanNamesForType(type)).containsExactly("memberOidcUserService");
  }

  @Test
  void sessionsAreKeptInPostgres() {
    // When / Then
    assertThat(sessions).isInstanceOf(JdbcIndexedSessionRepository.class);
  }
}
