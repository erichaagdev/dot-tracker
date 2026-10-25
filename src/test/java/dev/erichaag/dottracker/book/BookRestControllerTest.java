package dev.erichaag.dottracker.book;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.erichaag.dottracker.book.BookService.BookDetails;
import dev.erichaag.dottracker.security.SecurityConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
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

@WebMvcTest(BookRestController.class)
@Import(SecurityConfiguration.class)
class BookRestControllerTest {

  private static final UUID ANA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3d");

  private static final UUID BEA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3e");

  private static final UUID BOOK = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3f");

  private static final UUID DOT_BY_LETTER = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c40");

  private static final String CABIN_GETAWAY = """
    {
      "productLineId": "0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c40",
      "name": "Cabin Getaway",
      "availability": "AVAILABLE",
      "listings": [
        {"retailer": "Turner Toys", "priceCents": 1499, "url": "https://turnertoys.com/", "exclusive": false},
        {"retailer": "Target", "priceCents": null, "url": null, "exclusive": true}
      ]
    }
    """;

  @Autowired
  MockMvc mvc;

  @MockitoBean
  BookService service;

  @Test
  void theCatalogComesBackWithListingsAndWhetherEachBookIsAStoreExclusive() throws Exception {
    // Given
    when(service.findAll()).thenReturn(List.of(cabinGetaway()));

    // When / Then
    mvc.perform(get("/api/books").with(member()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value(BOOK.toString()))
      .andExpect(jsonPath("$[0].productLineId").value(DOT_BY_LETTER.toString()))
      .andExpect(jsonPath("$[0].name").value("Cabin Getaway"))
      .andExpect(jsonPath("$[0].availability").value("AVAILABLE"))
      .andExpect(jsonPath("$[0].pageCount").value(20))
      .andExpect(jsonPath("$[0].listings[0].retailer").value("Turner Toys"))
      .andExpect(jsonPath("$[0].listings[0].priceCents").value(1499))
      .andExpect(jsonPath("$[0].listings[0].url").value("https://turnertoys.com/"))
      .andExpect(jsonPath("$[0].listings[0].exclusive").value(false))
      .andExpect(jsonPath("$[0].listings[1].retailer").value("Target"))
      .andExpect(jsonPath("$[0].listings[1].priceCents").value(nullValue()))
      .andExpect(jsonPath("$[0].listings[1].url").value(nullValue()))
      .andExpect(jsonPath("$[0].listings[1].exclusive").value(true))
      .andExpect(jsonPath("$[0].exclusive").value(true));
  }

  @Test
  void oneBookComesBackById() throws Exception {
    // Given
    when(service.getById(BOOK)).thenReturn(cabinGetaway());

    // When / Then
    mvc.perform(get("/api/books/{id}", BOOK).with(member()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(BOOK.toString()))
      .andExpect(jsonPath("$.name").value("Cabin Getaway"))
      .andExpect(jsonPath("$.exclusive").value(true));
  }

  @Test
  void aBookThatIsGoneAnswers404WithTheReason() throws Exception {
    // Given
    when(service.getById(BOOK)).thenThrow(new NoSuchElementException("That book isn't in the catalog anymore."));

    // When / Then
    mvc.perform(get("/api/books/{id}", BOOK).with(member()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.detail").value("That book isn't in the catalog anymore."));
  }

  @Test
  void withoutASessionTheCatalogAnswers401RatherThanARedirect() throws Exception {
    // When / Then
    mvc.perform(get("/api/books").accept(MediaType.TEXT_HTML))
      .andExpect(status().isUnauthorized())
      .andExpect(header().doesNotExist("Location"));
    mvc.perform(get("/api/books/{id}", BOOK)).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  @Test
  void anAdminAddsABookWhichGetsItsLinesPageCountWhenItHasNone() throws Exception {
    // Given
    when(service.add(any())).thenReturn(cabinGetaway());

    // When / Then
    mvc.perform(withBody(post("/api/books"), CABIN_GETAWAY).with(admin()).with(csrf()))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").value(BOOK.toString()))
      .andExpect(jsonPath("$.name").value("Cabin Getaway"));
    verify(service).add(new BookDetails(DOT_BY_LETTER, "Cabin Getaway", Availability.AVAILABLE, null, listings()));
  }

  @Test
  void anAdminChangesABook() throws Exception {
    // Given
    when(service.update(eq(BOOK), any())).thenReturn(cabinGetaway());
    var body = CABIN_GETAWAY.replace("\"name\"", "\"pageCount\": 24, \"name\"");

    // When / Then
    mvc.perform(withBody(put("/api/books/{id}", BOOK), body).with(admin()).with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(BOOK.toString()));
    verify(service).update(BOOK, new BookDetails(DOT_BY_LETTER, "Cabin Getaway", Availability.AVAILABLE, 24,
      listings()));
  }

  @Test
  void anAdminDeletesABook() throws Exception {
    // When / Then
    mvc.perform(delete("/api/books/{id}", BOOK).with(admin()).with(csrf()))
      .andExpect(status().isNoContent());
    verify(service).delete(BOOK);
  }

  @Test
  void deletingABookSomeoneTracksAnswers400AskingForItToBeRetired() throws Exception {
    // Given
    doThrow(new IllegalArgumentException(
      "Someone in the group is already tracking this book, so it can't be deleted. Mark it as retired instead."))
      .when(service).delete(BOOK);

    // When / Then
    mvc.perform(delete("/api/books/{id}", BOOK).with(admin()).with(csrf()))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value(
        "Someone in the group is already tracking this book, so it can't be deleted. Mark it as retired instead."));
  }

  @Test
  void aMemberCannotAddChangeOrDeleteBooks() throws Exception {
    // When / Then
    mvc.perform(withBody(post("/api/books"), CABIN_GETAWAY).with(member()).with(csrf()))
      .andExpect(status().isForbidden())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("Only an admin can do that."));
    mvc.perform(withBody(put("/api/books/{id}", BOOK), CABIN_GETAWAY).with(member()).with(csrf()))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.detail").value("Only an admin can do that."));
    mvc.perform(delete("/api/books/{id}", BOOK).with(member()).with(csrf()))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.detail").value("Only an admin can do that."));
    verifyNoInteractions(service);
  }

  @Test
  void changingTheCatalogNeedsTheCsrfToken() throws Exception {
    // When / Then
    mvc.perform(withBody(post("/api/books"), CABIN_GETAWAY).with(admin())).andExpect(status().isForbidden());
    mvc.perform(withBody(put("/api/books/{id}", BOOK), CABIN_GETAWAY).with(admin()))
      .andExpect(status().isForbidden());
    mvc.perform(delete("/api/books/{id}", BOOK).with(admin())).andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void withoutASessionChangingTheCatalogAnswers401() throws Exception {
    // When / Then
    mvc.perform(withBody(post("/api/books"), CABIN_GETAWAY).with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/books/{id}", BOOK).with(csrf())).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  @Test
  void aNameAlreadyInTheLineAnswers400WithTheReason() throws Exception {
    // Given
    when(service.add(any()))
      .thenThrow(new IllegalArgumentException("Dot by Letter already has a book called Cabin Getaway."));

    // When / Then
    mvc.perform(withBody(post("/api/books"), CABIN_GETAWAY).with(admin()).with(csrf()))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("Dot by Letter already has a book called Cabin Getaway."));
  }

  @Test
  void aStoreWithoutANameAnswers400WithTheReason() throws Exception {
    // Given
    var body = CABIN_GETAWAY.replace("\"retailer\": \"Target\"", "\"retailer\": \"  \"");

    // When / Then
    mvc.perform(withBody(post("/api/books"), body).with(admin()).with(csrf()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.detail").value("Every store needs a name."));
    verifyNoInteractions(service);
  }

  @Test
  void aStoreLinkThatIsNotAWebAddressAnswers400WithTheReason() throws Exception {
    // Given
    var body = CABIN_GETAWAY.replace("\"url\": \"https://turnertoys.com/\"", "\"url\": \"javascript:alert(1)\"");

    // When / Then
    mvc.perform(withBody(post("/api/books"), body).with(admin()).with(csrf()))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("A store link has to start with http:// or https://."));
    verifyNoInteractions(service);
  }

  @Test
  void aBookWithoutANameOrProductLineIsRefused() throws Exception {
    // When / Then
    mvc.perform(withBody(post("/api/books"), CABIN_GETAWAY.replace("\"name\"", "\"title\"")).with(admin()).with(csrf()))
      .andExpect(status().isBadRequest());
    mvc.perform(withBody(post("/api/books"), CABIN_GETAWAY.replace("\"productLineId\"", "\"line\""))
        .with(admin()).with(csrf()))
      .andExpect(status().isBadRequest());
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

  private static List<Listing> listings() {
    return List.of(new Listing("Turner Toys", 1499, "https://turnertoys.com/", false),
      new Listing("Target", null, null, true));
  }

  private static Book cabinGetaway() {
    var book = new Book(DOT_BY_LETTER, "Cabin Getaway", Availability.AVAILABLE, 20, listings(),
      Instant.parse("2026-10-01T15:30:00Z"));
    ReflectionTestUtils.setField(book, "id", BOOK);
    return book;
  }
}
