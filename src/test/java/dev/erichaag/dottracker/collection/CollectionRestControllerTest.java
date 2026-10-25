package dev.erichaag.dottracker.collection;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.erichaag.dottracker.collection.CollectionService.TrackedBook;
import dev.erichaag.dottracker.security.CurrentMember;
import dev.erichaag.dottracker.security.SecurityConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CollectionRestController.class)
@Import({SecurityConfiguration.class, CurrentMember.class})
class CollectionRestControllerTest {

  private static final UUID BEA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3e");

  private static final UUID BOOK = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3f");

  private static final Clock STARTED = Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), ZoneId.of("America/Chicago"));

  private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-25T17:00:00Z"), ZoneId.of("America/Chicago"));

  @Autowired
  MockMvc mvc;

  @MockitoBean
  CollectionService service;

  @Test
  void theSignedInMembersCollectionComesBackWithEveryField() throws Exception {
    // Given
    when(service.findAll(BEA)).thenReturn(List.of(new TrackedBook(cabinGetaway(), 20)));

    // When / Then
    mvc.perform(get("/api/collection").with(bea()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].bookId").value(BOOK.toString()))
      .andExpect(jsonPath("$[0].owned").value(true))
      .andExpect(jsonPath("$[0].wanted").value("MAYBE"))
      .andExpect(jsonPath("$[0].progress").value("IN_PROGRESS"))
      .andExpect(jsonPath("$[0].pagesDone", contains(1, 2, 3)))
      .andExpect(jsonPath("$[0].ratings.overall").value(4))
      .andExpect(jsonPath("$[0].ratings.design").value(5))
      .andExpect(jsonPath("$[0].ratings.color").value(nullValue()))
      .andExpect(jsonPath("$[0].notes").value("Saving the lake page for last."))
      .andExpect(jsonPath("$[0].startedOn").value("2026-09-28"))
      .andExpect(jsonPath("$[0].finishedOn").value(nullValue()))
      .andExpect(jsonPath("$[0].updatedAt").value("2026-10-25T17:00:00Z"));
  }

  @Test
  void aNewEntryHasNoAnswersYet() throws Exception {
    // Given
    when(service.changeOwned(BEA, BOOK, false))
      .thenReturn(new TrackedBook(new CollectionEntry(BEA, BOOK, NOW), 20));

    // When / Then
    mvc.perform(withBody(put("/api/collection/{bookId}/owned", BOOK), "{\"owned\": false}").with(bea()).with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.owned").value(false))
      .andExpect(jsonPath("$.wanted").value(nullValue()))
      .andExpect(jsonPath("$.progress").value("NOT_STARTED"))
      .andExpect(jsonPath("$.pagesDone", empty()))
      .andExpect(jsonPath("$.ratings.overall").value(nullValue()))
      .andExpect(jsonPath("$.notes").value(nullValue()))
      .andExpect(jsonPath("$.startedOn").value(nullValue()));
  }

  @Test
  void pagesBeyondTheBooksPageCountAreLeftOut() throws Exception {
    // Given
    var entry = new CollectionEntry(BEA, BOOK, STARTED);
    List.of(1, 2, 19, 20).forEach(page -> entry.markPageDone(page, 20, NOW));
    when(service.findAll(BEA)).thenReturn(List.of(new TrackedBook(entry, 18)));

    // When / Then
    mvc.perform(get("/api/collection").with(bea()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].pagesDone", contains(1, 2)));
  }

  @Test
  void withoutASessionTheCollectionAnswers401RatherThanARedirect() throws Exception {
    // When / Then
    mvc.perform(get("/api/collection").accept(MediaType.TEXT_HTML))
      .andExpect(status().isUnauthorized())
      .andExpect(header().doesNotExist("Location"));
    mvc.perform(put("/api/collection/{bookId}/pages/{page}", BOOK, 3).with(csrf()))
      .andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  @Test
  void markingOwnedAnswersWithTheUpdatedEntry() throws Exception {
    // Given
    when(service.changeOwned(BEA, BOOK, true)).thenReturn(tracked());

    // When / Then
    mvc.perform(withBody(put("/api/collection/{bookId}/owned", BOOK), "{\"owned\": true}").with(bea()).with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.bookId").value(BOOK.toString()))
      .andExpect(jsonPath("$.owned").value(true));
    verify(service).changeOwned(BEA, BOOK, true);
  }

  @Test
  void markingWantedAnswersWithTheUpdatedEntry() throws Exception {
    // Given
    when(service.changeWanted(BEA, BOOK, Wanted.MAYBE)).thenReturn(tracked());

    // When / Then
    mvc.perform(withBody(put("/api/collection/{bookId}/wanted", BOOK), "{\"wanted\": \"MAYBE\"}")
        .with(bea()).with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.wanted").value("MAYBE"));
    verify(service).changeWanted(BEA, BOOK, Wanted.MAYBE);
  }

  @Test
  void aNullWantedClearsTheAnswer() throws Exception {
    // Given
    when(service.changeWanted(BEA, BOOK, null)).thenReturn(tracked());

    // When / Then
    mvc.perform(withBody(put("/api/collection/{bookId}/wanted", BOOK), "{\"wanted\": null}")
        .with(bea()).with(csrf()))
      .andExpect(status().isOk());
    verify(service).changeWanted(BEA, BOOK, null);
  }

  @Test
  void settingProgressAnswersWithTheUpdatedEntry() throws Exception {
    // Given
    when(service.changeProgress(BEA, BOOK, Progress.IN_PROGRESS)).thenReturn(tracked());

    // When / Then
    mvc.perform(withBody(put("/api/collection/{bookId}/progress", BOOK), "{\"progress\": \"IN_PROGRESS\"}")
        .with(bea()).with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.progress").value("IN_PROGRESS"));
    verify(service).changeProgress(BEA, BOOK, Progress.IN_PROGRESS);
  }

  @Test
  void markingAPageDoneAnswersWithTheUpdatedEntry() throws Exception {
    // Given
    when(service.markPageDone(BEA, BOOK, 3)).thenReturn(tracked());

    // When / Then
    mvc.perform(put("/api/collection/{bookId}/pages/{page}", BOOK, 3).with(bea()).with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.pagesDone", contains(1, 2, 3)));
    verify(service).markPageDone(BEA, BOOK, 3);
  }

  @Test
  void markingAPageNotDoneAnswersWithTheUpdatedEntry() throws Exception {
    // Given
    when(service.markPageNotDone(BEA, BOOK, 4)).thenReturn(tracked());

    // When / Then
    mvc.perform(delete("/api/collection/{bookId}/pages/{page}", BOOK, 4).with(bea()).with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.bookId").value(BOOK.toString()));
    verify(service).markPageNotDone(BEA, BOOK, 4);
  }

  @Test
  void ratingsAreSetTogetherAndANullClearsOne() throws Exception {
    // Given
    when(service.rate(BEA, BOOK, 4, 5, null)).thenReturn(tracked());

    // When / Then
    mvc.perform(withBody(put("/api/collection/{bookId}/ratings", BOOK), """
        {"overall": 4, "design": 5, "color": null}
        """).with(bea()).with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.ratings.overall").value(4))
      .andExpect(jsonPath("$.ratings.design").value(5))
      .andExpect(jsonPath("$.ratings.color").value(nullValue()));
    verify(service).rate(BEA, BOOK, 4, 5, null);
  }

  @Test
  void aMissingRatingIsClearedToo() throws Exception {
    // Given
    when(service.rate(BEA, BOOK, 3, null, null)).thenReturn(tracked());

    // When / Then
    mvc.perform(withBody(put("/api/collection/{bookId}/ratings", BOOK), "{\"overall\": 3}").with(bea()).with(csrf()))
      .andExpect(status().isOk());
    verify(service).rate(BEA, BOOK, 3, null, null);
  }

  @Test
  void notesAnswerWithTheUpdatedEntry() throws Exception {
    // Given
    when(service.changeNotes(BEA, BOOK, "Saving the lake page for last.")).thenReturn(tracked());

    // When / Then
    mvc.perform(withBody(put("/api/collection/{bookId}/notes", BOOK), "{\"notes\": \"Saving the lake page for last.\"}")
        .with(bea()).with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.notes").value("Saving the lake page for last."));
    verify(service).changeNotes(BEA, BOOK, "Saving the lake page for last.");
  }

  @Test
  void everyChangeNeedsTheCsrfToken() throws Exception {
    // When / Then
    mvc.perform(withBody(put("/api/collection/{bookId}/owned", BOOK), "{\"owned\": true}").with(bea()))
      .andExpect(status().isForbidden());
    mvc.perform(withBody(put("/api/collection/{bookId}/wanted", BOOK), "{\"wanted\": \"YES\"}").with(bea()))
      .andExpect(status().isForbidden());
    mvc.perform(withBody(put("/api/collection/{bookId}/progress", BOOK), "{\"progress\": \"FINISHED\"}").with(bea()))
      .andExpect(status().isForbidden());
    mvc.perform(put("/api/collection/{bookId}/pages/{page}", BOOK, 3).with(bea()))
      .andExpect(status().isForbidden());
    mvc.perform(delete("/api/collection/{bookId}/pages/{page}", BOOK, 3).with(bea()))
      .andExpect(status().isForbidden());
    mvc.perform(withBody(put("/api/collection/{bookId}/ratings", BOOK), "{\"overall\": 4}").with(bea()))
      .andExpect(status().isForbidden());
    mvc.perform(withBody(put("/api/collection/{bookId}/notes", BOOK), "{\"notes\": \"Lovely.\"}").with(bea()))
      .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void aBookThatIsGoneAnswers404WithTheReason() throws Exception {
    // Given
    when(service.markPageDone(BEA, BOOK, 3))
      .thenThrow(new NoSuchElementException("That book isn't in the catalog anymore."));

    // When / Then
    mvc.perform(put("/api/collection/{bookId}/pages/{page}", BOOK, 3).with(bea()).with(csrf()))
      .andExpect(status().isNotFound())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("That book isn't in the catalog anymore."));
  }

  @Test
  void aPageOutsideTheBookAnswers400WithTheReason() throws Exception {
    // Given
    when(service.markPageDone(BEA, BOOK, 21))
      .thenThrow(new IllegalArgumentException("There's no page 21 in this book. Its pages go from 1 to 20."));

    // When / Then
    mvc.perform(put("/api/collection/{bookId}/pages/{page}", BOOK, 21).with(bea()).with(csrf()))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("There's no page 21 in this book. Its pages go from 1 to 20."));
  }

  @Test
  void aRatingOutsideZeroToFiveAnswers400WithTheReason() throws Exception {
    // Given
    when(service.rate(BEA, BOOK, 6, null, null))
      .thenThrow(new IllegalArgumentException("A rating must be between 0 and 5."));

    // When / Then
    mvc.perform(withBody(put("/api/collection/{bookId}/ratings", BOOK), "{\"overall\": 6}").with(bea()).with(csrf()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.detail").value("A rating must be between 0 and 5."));
  }

  @Test
  void aPageThatIsNotANumberIsRefused() throws Exception {
    // When / Then
    mvc.perform(put("/api/collection/{bookId}/pages/{page}", BOOK, "first").with(bea()).with(csrf()))
      .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void ownedAndProgressNeedAValue() throws Exception {
    // When / Then
    mvc.perform(withBody(put("/api/collection/{bookId}/owned", BOOK), "{}").with(bea()).with(csrf()))
      .andExpect(status().isBadRequest());
    mvc.perform(withBody(put("/api/collection/{bookId}/progress", BOOK), "{}").with(bea()).with(csrf()))
      .andExpect(status().isBadRequest());
    mvc.perform(withBody(put("/api/collection/{bookId}/progress", BOOK), "{\"progress\": \"HALFWAY\"}")
        .with(bea()).with(csrf()))
      .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static RequestPostProcessor bea() {
    return oidcLogin()
      .idToken(token -> token.subject(BEA.toString()))
      .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
  }

  private static TrackedBook tracked() {
    return new TrackedBook(cabinGetaway(), 20);
  }

  private static CollectionEntry cabinGetaway() {
    var entry = new CollectionEntry(BEA, BOOK, STARTED);
    entry.markPageDone(1, 20, STARTED);
    entry.markPageDone(2, 20, NOW);
    entry.markPageDone(3, 20, NOW);
    entry.changeWanted(Wanted.MAYBE, NOW);
    entry.rate(4, 5, null, NOW);
    entry.changeNotes("Saving the lake page for last.", NOW);
    return entry;
  }
}
