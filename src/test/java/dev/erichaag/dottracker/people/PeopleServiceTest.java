package dev.erichaag.dottracker.people;

import static dev.erichaag.dottracker.FixedClockConfiguration.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import dev.erichaag.dottracker.DotTrackerProperties;
import dev.erichaag.dottracker.FixedClockConfiguration;
import dev.erichaag.dottracker.PostgresTestConfiguration;
import dev.erichaag.dottracker.book.BookRepository;
import dev.erichaag.dottracker.book.BookService;
import dev.erichaag.dottracker.book.ProductLineRepository;
import dev.erichaag.dottracker.collection.CollectionService;
import dev.erichaag.dottracker.collection.CollectionService.Counts;
import dev.erichaag.dottracker.collection.Progress;
import dev.erichaag.dottracker.collection.Wanted;
import dev.erichaag.dottracker.invite.InviteService;
import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.member.MemberRepository;
import dev.erichaag.dottracker.member.MemberService;
import dev.erichaag.dottracker.people.PeopleService.MemberEntry;
import dev.erichaag.dottracker.people.PeopleService.Person;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.session.jdbc.config.annotation.web.http.JdbcHttpSessionConfiguration;

// The real migrations run, so every test starts from the seeded catalog, where Dot by Letter books have 20 pages.
// MemberService needs Spring Session, so this imports the configuration Spring Boot imports for the app.
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = NONE)
@Import({
  PostgresTestConfiguration.class,
  FixedClockConfiguration.class,
  JdbcHttpSessionConfiguration.class,
  PeopleService.class,
  MemberService.class,
  InviteService.class,
  CollectionService.class,
  BookService.class,
})
@EnableConfigurationProperties(DotTrackerProperties.class)
class PeopleServiceTest {

  @Autowired
  PeopleService service;

  @Autowired
  CollectionService collections;

  @Autowired
  MemberRepository members;

  @Autowired
  BookRepository books;

  @Autowired
  ProductLineRepository productLines;

  @Autowired
  TestEntityManager entityManager;

  @Autowired
  EntityManagerFactory entityManagerFactory;

  UUID ana = UUID.randomUUID();

  UUID bea = UUID.randomUUID();

  UUID cam = UUID.randomUUID();

  UUID cabinGetaway = UUID.randomUUID();

  UUID cornerCafe = UUID.randomUUID();

  @BeforeEach
  void saveMembers() {
    cam = save("cam@example.com", "Cam", Role.MEMBER);
    ana = save("ana@example.com", "Ana", Role.ADMIN);
    bea = save("bea@example.com", "Bea", Role.MEMBER);
    cabinGetaway = book("Dot by Letter", "Cabin Getaway");
    cornerCafe = book("Dot by Letter", "Corner Cafe");
  }

  @Test
  void everyoneComesBackByNameWithWhatTheyOwnAreWorkingOnAndFinished() {
    // Given
    collections.markPageDone(ana, cabinGetaway, 1);
    collections.changeProgress(ana, cornerCafe, Progress.FINISHED);
    collections.changeOwned(ana, book("Dot by Letter", "Cozy Companions"), true);
    collections.changeWanted(ana, book("Dot by Letter", "Holiday Helpers"), Wanted.YES);
    collections.changeProgress(bea, cabinGetaway, Progress.FINISHED);

    // When
    var people = service.findAll();

    // Then
    assertThat(people)
      .extracting(person -> person.member().getDisplayName(), Person::counts)
      .containsExactly(
        tuple("Ana", new Counts(3, 1, 1)),
        tuple("Bea", new Counts(1, 0, 1)),
        tuple("Cam", Counts.NONE)
      );
  }

  @Test
  void everyoneLoadsInTwoQueriesHoweverManyBooksTheyTrack() {
    // Given
    trackFiveBooksForEveryone();
    var statistics = freshStatistics();

    // When
    var people = service.findAll();

    // Then
    assertThat(people).hasSize(3);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  void aPersonsCollectionComesBackWithEachBooksPageCount() {
    // Given
    collections.markPageDone(bea, cabinGetaway, 3);
    collections.changeNotes(bea, cabinGetaway, "Saving the lake page for last.");
    collections.changeOwned(ana, cornerCafe, true);

    // When
    var collection = service.findCollection(bea);

    // Then
    assertThat(collection).singleElement().satisfies(tracked -> {
      assertThat(tracked.entry().getBookId()).isEqualTo(cabinGetaway);
      assertThat(tracked.pageCount()).isEqualTo(20);
      assertThat(tracked.entry().getPagesDone(tracked.pageCount())).containsExactly(3);
      assertThat(tracked.entry().getNotes()).isEqualTo("Saving the lake page for last.");
    });
  }

  @Test
  void aPersonsCollectionLoadsInThreeQueriesHoweverManyBooksTheyTrack() {
    // Given
    trackFiveBooksForEveryone();
    var statistics = freshStatistics();

    // When
    var collection = service.findCollection(bea);

    // Then
    assertThat(collection).hasSize(5);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
  }

  @Test
  void someoneWhoIsNotInTheGroupIsReported() {
    // When / Then
    assertThatExceptionOfType(NoSuchElementException.class)
      .isThrownBy(() -> service.findCollection(UUID.randomUUID()))
      .withMessage("That person isn't in the group.");
  }

  @Test
  void everyonesEntriesForABookComeBackByNameWithTheGroupsAverageRating() {
    // Given
    collections.markPageDone(cam, cabinGetaway, 1);
    collections.rate(cam, cabinGetaway, 4, null, null);
    collections.changeProgress(ana, cabinGetaway, Progress.FINISHED);
    collections.rate(ana, cabinGetaway, 5, 3, null);
    collections.changeWanted(bea, cabinGetaway, Wanted.MAYBE);
    collections.rate(bea, cornerCafe, 0, null, null);

    // When
    var entries = service.findEntries(cabinGetaway);

    // Then
    assertThat(entries.entries())
      .extracting(entry -> entry.member().getDisplayName(), entry -> entry.tracked().entry().getProgress(),
        entry -> entry.tracked().pageCount())
      .containsExactly(
        tuple("Ana", Progress.FINISHED, 20),
        tuple("Bea", Progress.NOT_STARTED, 20),
        tuple("Cam", Progress.IN_PROGRESS, 20)
      );
    assertThat(entries.averageRating()).isEqualTo(4.5);
    assertThat(entries.ratingCount()).isEqualTo(2);
  }

  @Test
  void aRatingOfZeroCountsTowardTheAverage() {
    // Given
    collections.rate(ana, cabinGetaway, 0, null, null);
    collections.rate(bea, cabinGetaway, 3, null, null);

    // When
    var entries = service.findEntries(cabinGetaway);

    // Then
    assertThat(entries.averageRating()).isEqualTo(1.5);
    assertThat(entries.ratingCount()).isEqualTo(2);
  }

  @Test
  void aBookNobodyRatedHasNoAverage() {
    // Given
    collections.changeOwned(bea, cabinGetaway, true);

    // When
    var entries = service.findEntries(cabinGetaway);

    // Then
    assertThat(entries.entries()).extracting(MemberEntry::member).extracting(Member::getDisplayName)
      .containsExactly("Bea");
    assertThat(entries.averageRating()).isNull();
    assertThat(entries.ratingCount()).isZero();
  }

  @Test
  void aBooksEntriesLoadInThreeQueriesHoweverManyPeopleTrackIt() {
    // Given
    trackFiveBooksForEveryone();
    var statistics = freshStatistics();

    // When
    var entries = service.findEntries(cabinGetaway);

    // Then
    assertThat(entries.entries()).hasSize(3);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
  }

  @Test
  void aBookThatIsGoneIsReported() {
    // When / Then
    assertThatExceptionOfType(NoSuchElementException.class)
      .isThrownBy(() -> service.findEntries(UUID.randomUUID()))
      .withMessage("That book isn't in the catalog anymore.");
  }

  /** Cabin Getaway and four more books for each member, each with a page done, ratings and notes. */
  private void trackFiveBooksForEveryone() {
    var fourMore = books.findAllByOrderByNameAsc().stream()
      .map(book -> Objects.requireNonNull(book.getId()))
      .filter(book -> !book.equals(cabinGetaway))
      .limit(4)
      .toList();
    for (var member : List.of(ana, bea, cam)) {
      Stream.concat(Stream.of(cabinGetaway), fourMore.stream()).forEach(book -> {
        collections.markPageDone(member, book, 2);
        collections.rate(member, book, 4, 3, null);
        collections.changeNotes(member, book, "Lovely.");
      });
    }
  }

  // Saved rows would otherwise come back from the session without a query
  private Statistics freshStatistics() {
    entityManager.flush();
    entityManager.clear();
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    return statistics;
  }

  private UUID save(String email, String name, Role role) {
    return Objects.requireNonNull(members.save(new Member(email, name, role, NOW)).getId());
  }

  private UUID book(String productLine, String name) {
    var lineId = productLines.findByNameIgnoreCase(productLine).orElseThrow().getId();
    return books.findAll().stream()
      .filter(book -> book.getProductLineId().equals(lineId) && book.getName().equals(name))
      .map(book -> Objects.requireNonNull(book.getId()))
      .findFirst()
      .orElseThrow();
  }
}
