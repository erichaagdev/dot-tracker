package dev.erichaag.dottracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
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
        insert into product_line (name, default_page_count, sort_order, version)
        values ('Test line', 20, 99, 0)
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
}
