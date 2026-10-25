package dev.erichaag.dottracker.book;

import static dev.erichaag.dottracker.book.LineColor.DBL;
import static dev.erichaag.dottracker.book.LineColor.PREMIUM;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.erichaag.dottracker.security.SecurityConfiguration;
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

@WebMvcTest(ProductLineRestController.class)
@Import(SecurityConfiguration.class)
class ProductLineRestControllerTest {

  private static final UUID ANA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3d");

  private static final UUID BEA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3e");

  private static final UUID DOT_BY_LETTER = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c40");

  private static final UUID LARGE_DOT = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c41");

  private static final String LARGE_DOT_BODY = """
    {"name": "Dot by Letter Large Dot", "defaultPageCount": 25, "sortOrder": 3, "color": "PREMIUM"}
    """;

  @Autowired
  MockMvc mvc;

  @MockitoBean
  BookService service;

  @Test
  void productLinesComeBackInSortOrder() throws Exception {
    // Given
    when(service.findProductLines()).thenReturn(List.of(
      line(DOT_BY_LETTER, "Dot by Letter", 20, 1, DBL),
      line(LARGE_DOT, "Dot by Letter Large Dot", 25, 3, PREMIUM)
    ));

    // When / Then
    mvc.perform(get("/api/product-lines").with(member()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value(DOT_BY_LETTER.toString()))
      .andExpect(jsonPath("$[0].name").value("Dot by Letter"))
      .andExpect(jsonPath("$[0].color").value("DBL"))
      .andExpect(jsonPath("$[1].id").value(LARGE_DOT.toString()))
      .andExpect(jsonPath("$[1].name").value("Dot by Letter Large Dot"))
      .andExpect(jsonPath("$[1].defaultPageCount").value(25))
      .andExpect(jsonPath("$[1].sortOrder").value(3))
      .andExpect(jsonPath("$[1].color").value("PREMIUM"));
  }

  @Test
  void withoutASessionProductLinesAnswer401RatherThanARedirect() throws Exception {
    // When / Then
    mvc.perform(get("/api/product-lines").accept(MediaType.TEXT_HTML))
      .andExpect(status().isUnauthorized())
      .andExpect(header().doesNotExist("Location"));
    verifyNoInteractions(service);
  }

  @Test
  void anAdminAddsAProductLine() throws Exception {
    // Given
    when(service.addProductLine("Dot by Letter Large Dot", 25, 3, PREMIUM))
      .thenReturn(line(LARGE_DOT, "Dot by Letter Large Dot", 25, 3, PREMIUM));

    // When / Then
    mvc.perform(withBody(post("/api/product-lines")).with(admin()).with(csrf()))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").value(LARGE_DOT.toString()))
      .andExpect(jsonPath("$.name").value("Dot by Letter Large Dot"))
      .andExpect(jsonPath("$.defaultPageCount").value(25))
      .andExpect(jsonPath("$.sortOrder").value(3))
      .andExpect(jsonPath("$.color").value("PREMIUM"));
    verify(service).addProductLine("Dot by Letter Large Dot", 25, 3, PREMIUM);
  }

  @Test
  void anAdminChangesAProductLine() throws Exception {
    // Given
    when(service.updateProductLine(LARGE_DOT, "Dot by Letter Large Dot", 25, 3, PREMIUM))
      .thenReturn(line(LARGE_DOT, "Dot by Letter Large Dot", 25, 3, PREMIUM));

    // When / Then
    mvc.perform(withBody(put("/api/product-lines/{id}", LARGE_DOT)).with(admin()).with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(LARGE_DOT.toString()));
    verify(service).updateProductLine(LARGE_DOT, "Dot by Letter Large Dot", 25, 3, PREMIUM);
  }

  @Test
  void aMemberCannotAddOrChangeProductLines() throws Exception {
    // When / Then
    mvc.perform(withBody(post("/api/product-lines")).with(member()).with(csrf()))
      .andExpect(status().isForbidden())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("Only an admin can do that."));
    mvc.perform(withBody(put("/api/product-lines/{id}", LARGE_DOT)).with(member()).with(csrf()))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.detail").value("Only an admin can do that."));
    verifyNoInteractions(service);
  }

  @Test
  void changingProductLinesNeedsTheCsrfToken() throws Exception {
    // When / Then
    mvc.perform(withBody(post("/api/product-lines")).with(admin())).andExpect(status().isForbidden());
    mvc.perform(withBody(put("/api/product-lines/{id}", LARGE_DOT)).with(admin())).andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void withoutASessionChangingProductLinesAnswers401() throws Exception {
    // When / Then
    mvc.perform(withBody(post("/api/product-lines")).with(csrf())).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  @Test
  void aNameAnotherLineHasAnswers400WithTheReason() throws Exception {
    // Given
    when(service.addProductLine("Dot by Letter Large Dot", 25, 3, PREMIUM))
      .thenThrow(new IllegalArgumentException("There's already a product line called Dot by Letter Large Dot."));

    // When / Then
    mvc.perform(withBody(post("/api/product-lines")).with(admin()).with(csrf()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.detail").value("There's already a product line called Dot by Letter Large Dot."));
  }

  @Test
  void aProductLineWithoutAPageCountIsRefused() throws Exception {
    // When / Then
    mvc.perform(post("/api/product-lines")
        .with(admin())
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"name": "Dot by Letter Large Dot", "sortOrder": 3}
          """))
      .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void aProductLineWithoutAColorIsRefused() throws Exception {
    // When / Then
    mvc.perform(post("/api/product-lines")
        .with(admin())
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"name": "Dot by Letter Large Dot", "defaultPageCount": 25, "sortOrder": 3}
          """))
      .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void aColorOutsideTheFamiliesIsRefused() throws Exception {
    // When / Then
    mvc.perform(post("/api/product-lines")
        .with(admin())
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"name": "Dot by Letter Large Dot", "defaultPageCount": 25, "sortOrder": 3, "color": "CHARTREUSE"}
          """))
      .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request) {
    return request.contentType(MediaType.APPLICATION_JSON).content(LARGE_DOT_BODY);
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

  private static ProductLine line(UUID id, String name, int defaultPageCount, int sortOrder, LineColor color) {
    var line = new ProductLine(name, defaultPageCount, sortOrder, color);
    ReflectionTestUtils.setField(line, "id", id);
    return line;
  }
}
