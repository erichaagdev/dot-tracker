package dev.erichaag.dottracker.people;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.erichaag.dottracker.collection.CollectionEntry;
import dev.erichaag.dottracker.collection.CollectionService.Counts;
import dev.erichaag.dottracker.collection.CollectionService.TrackedBook;
import dev.erichaag.dottracker.collection.Wanted;
import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.people.PeopleService.BookEntries;
import dev.erichaag.dottracker.people.PeopleService.MemberEntry;
import dev.erichaag.dottracker.people.PeopleService.Person;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PeopleRestController.class)
@Import(SecurityConfiguration.class)
class PeopleRestControllerTest {

  private static final UUID ANA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3d");

  private static final UUID BEA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3e");

  private static final UUID BOOK = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3f");

  private static final Clock STARTED = Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), ZoneId.of("America/Chicago"));

  private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-25T17:00:00Z"), ZoneId.of("America/Chicago"));

  @Autowired
  MockMvc mvc;

  @MockitoBean
  PeopleService service;

  @Test
  void aMemberSeesEveryoneWithTheirCountsButNoEmailOrRole() throws Exception {
    // Given
    when(service.findAll()).thenReturn(List.of(
      new Person(member(ANA, "ana@example.com", "Ana", Role.ADMIN), new Counts(12, 3, 8)),
      new Person(member(BEA, "bea@example.com", "Bea", Role.MEMBER), Counts.NONE)
    ));

    // When / Then
    mvc.perform(get("/api/people").with(bea()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value(ANA.toString()))
      .andExpect(jsonPath("$[0].displayName").value("Ana"))
      .andExpect(jsonPath("$[0].owned").value(12))
      .andExpect(jsonPath("$[0].inProgress").value(3))
      .andExpect(jsonPath("$[0].finished").value(8))
      .andExpect(jsonPath("$[0].email").doesNotExist())
      .andExpect(jsonPath("$[0].role").doesNotExist())
      .andExpect(jsonPath("$[1].displayName").value("Bea"))
      .andExpect(jsonPath("$[1].owned").value(0));
  }

  @Test
  void aMemberSeesSomeoneElsesCollectionWithEveryField() throws Exception {
    // Given
    when(service.findCollection(ANA)).thenReturn(List.of(new TrackedBook(cabinGetaway(ANA), 20)));

    // When / Then
    mvc.perform(get("/api/people/{id}/collection", ANA).with(bea()))
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
  void someoneWhoIsNotInTheGroupAnswers404WithTheReason() throws Exception {
    // Given
    when(service.findCollection(BEA)).thenThrow(new NoSuchElementException("That person isn't in the group."));

    // When / Then
    mvc.perform(get("/api/people/{id}/collection", BEA).with(bea()))
      .andExpect(status().isNotFound())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("That person isn't in the group."));
  }

  @Test
  void aMemberSeesEveryonesEntryForABookWithTheGroupsAverageRating() throws Exception {
    // Given
    when(service.findEntries(BOOK)).thenReturn(new BookEntries(List.of(
      new MemberEntry(member(ANA, "ana@example.com", "Ana", Role.ADMIN), new TrackedBook(cabinGetaway(ANA), 20)),
      new MemberEntry(member(BEA, "bea@example.com", "Bea", Role.MEMBER),
        new TrackedBook(new CollectionEntry(BEA, BOOK, NOW), 20))
    ), 4.5, 2));

    // When / Then
    mvc.perform(get("/api/books/{id}/entries", BOOK).with(bea()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.averageRating").value(4.5))
      .andExpect(jsonPath("$.ratingCount").value(2))
      .andExpect(jsonPath("$.entries[0].memberId").value(ANA.toString()))
      .andExpect(jsonPath("$.entries[0].displayName").value("Ana"))
      .andExpect(jsonPath("$.entries[0].email").doesNotExist())
      .andExpect(jsonPath("$.entries[0].role").doesNotExist())
      .andExpect(jsonPath("$.entries[0].entry.bookId").value(BOOK.toString()))
      .andExpect(jsonPath("$.entries[0].entry.progress").value("IN_PROGRESS"))
      .andExpect(jsonPath("$.entries[0].entry.pagesDone", contains(1, 2, 3)))
      .andExpect(jsonPath("$.entries[0].entry.ratings.overall").value(4))
      .andExpect(jsonPath("$.entries[0].entry.notes").value("Saving the lake page for last."))
      .andExpect(jsonPath("$.entries[1].displayName").value("Bea"))
      .andExpect(jsonPath("$.entries[1].entry.owned").value(false))
      .andExpect(jsonPath("$.entries[1].entry.pagesDone", empty()));
  }

  @Test
  void aBookNobodyRatedHasANullAverage() throws Exception {
    // Given
    when(service.findEntries(BOOK)).thenReturn(new BookEntries(List.of(), null, 0));

    // When / Then
    mvc.perform(get("/api/books/{id}/entries", BOOK).with(bea()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.averageRating").value(nullValue()))
      .andExpect(jsonPath("$.ratingCount").value(0))
      .andExpect(jsonPath("$.entries", empty()));
  }

  @Test
  void aBookThatIsGoneAnswers404WithTheReason() throws Exception {
    // Given
    when(service.findEntries(BOOK)).thenThrow(new NoSuchElementException("That book isn't in the catalog anymore."));

    // When / Then
    mvc.perform(get("/api/books/{id}/entries", BOOK).with(bea()))
      .andExpect(status().isNotFound())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("That book isn't in the catalog anymore."));
  }

  @Test
  void withoutASessionEveryRouteAnswers401RatherThanARedirect() throws Exception {
    // When / Then
    mvc.perform(get("/api/people").accept(MediaType.TEXT_HTML))
      .andExpect(status().isUnauthorized())
      .andExpect(header().doesNotExist("Location"));
    mvc.perform(get("/api/people/{id}/collection", ANA)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/books/{id}/entries", BOOK)).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  @Test
  void anIdThatIsNotAUuidIsRefused() throws Exception {
    // When / Then
    mvc.perform(get("/api/people/{id}/collection", "bea").with(bea())).andExpect(status().isBadRequest());
    mvc.perform(get("/api/books/{id}/entries", "cabin-getaway").with(bea())).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  private static RequestPostProcessor bea() {
    return oidcLogin()
      .idToken(token -> token.subject(BEA.toString()))
      .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
  }

  private static Member member(UUID id, String email, String name, Role role) {
    var member = new Member(email, name, role, Instant.parse("2026-10-01T15:30:00Z"));
    ReflectionTestUtils.setField(member, "id", id);
    return member;
  }

  private static CollectionEntry cabinGetaway(UUID memberId) {
    var entry = new CollectionEntry(memberId, BOOK, STARTED);
    entry.markPageDone(1, 20, STARTED);
    entry.markPageDone(2, 20, NOW);
    entry.markPageDone(3, 20, NOW);
    entry.changeWanted(Wanted.MAYBE, NOW);
    entry.rate(4, 5, null, NOW);
    entry.changeNotes("Saving the lake page for last.", NOW);
    return entry;
  }
}
