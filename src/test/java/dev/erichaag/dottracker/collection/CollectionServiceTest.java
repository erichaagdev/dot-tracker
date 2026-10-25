package dev.erichaag.dottracker.collection;

import static dev.erichaag.dottracker.FixedClockConfiguration.NOW;
import static dev.erichaag.dottracker.collection.Progress.FINISHED;
import static dev.erichaag.dottracker.collection.Progress.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import dev.erichaag.dottracker.FixedClockConfiguration;
import dev.erichaag.dottracker.PostgresTestConfiguration;
import dev.erichaag.dottracker.book.BookRepository;
import dev.erichaag.dottracker.book.BookService;
import dev.erichaag.dottracker.book.BookService.BookDetails;
import dev.erichaag.dottracker.book.ProductLineRepository;
import dev.erichaag.dottracker.collection.CollectionService.TrackedBook;
import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.member.MemberRepository;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

// The real migrations run, so every test starts from the seeded catalog, where Dot by Letter books have 20 pages and
// Dot by Letter Large Dot books have 25
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({PostgresTestConfiguration.class, FixedClockConfiguration.class, CollectionService.class, BookService.class})
class CollectionServiceTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-10-25");

  @Autowired
  CollectionService service;

  @Autowired
  BookService bookService;

  @Autowired
  CollectionEntryRepository repository;

  @Autowired
  BookRepository books;

  @Autowired
  ProductLineRepository productLines;

  @Autowired
  MemberRepository members;

  @Autowired
  TestEntityManager entityManager;

  @Autowired
  JdbcClient jdbc;

  UUID ana = UUID.randomUUID();

  UUID bea = UUID.randomUUID();

  @BeforeEach
  void saveMembers() {
    ana = Objects.requireNonNull(members.save(new Member("ana@example.com", "Ana", Role.ADMIN, NOW)).getId());
    bea = Objects.requireNonNull(members.save(new Member("bea@example.com", "Bea", Role.MEMBER, NOW)).getId());
  }

  @Test
  void theFirstChangeToABookCreatesTheMembersEntryForIt() {
    // Given
    var cabinGetaway = book("Dot by Letter", "Cabin Getaway");

    // When
    var tracked = service.changeOwned(bea, cabinGetaway, true);

    // Then
    assertThat(tracked.pageCount()).isEqualTo(20);
    var entry = repository.findByMemberIdAndBookId(bea, cabinGetaway).orElseThrow();
    assertThat(entry.getId()).isNotNull();
    assertThat(entry.isOwned()).isTrue();
    assertThat(entry.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  void laterChangesGoToTheSameEntry() {
    // Given
    var cabinGetaway = book("Dot by Letter", "Cabin Getaway");
    service.changeWanted(bea, cabinGetaway, Wanted.MAYBE);

    // When
    service.changeOwned(bea, cabinGetaway, true);
    service.rate(bea, cabinGetaway, 4, null, null);
    service.changeNotes(bea, cabinGetaway, "A gift from Ana.");

    // Then
    assertThat(repository.findByMemberId(bea)).singleElement().satisfies(entry -> {
      assertThat(entry.getWanted()).isEqualTo(Wanted.MAYBE);
      assertThat(entry.isOwned()).isTrue();
      assertThat(entry.getRating()).isEqualTo(4);
      assertThat(entry.getNotes()).isEqualTo("A gift from Ana.");
    });
  }

  @Test
  void aMemberSeesOnlyTheirOwnEntriesEachWithItsBooksPageCount() {
    // Given
    var cabinGetaway = book("Dot by Letter", "Cabin Getaway");
    var mandalas = book("Dot by Letter Large Dot", "Mandalas");
    service.changeOwned(bea, cabinGetaway, true);
    service.changeWanted(bea, mandalas, Wanted.YES);
    service.changeOwned(ana, cabinGetaway, true);

    // When
    var collection = service.findAll(bea);

    // Then
    assertThat(collection)
      .extracting(tracked -> tracked.entry().getMemberId(), tracked -> tracked.entry().getBookId(),
        TrackedBook::pageCount)
      .containsExactlyInAnyOrder(tuple(bea, cabinGetaway, 20), tuple(bea, mandalas, 25));
  }

  @Test
  void aBookThatIsGoneIsRefusedWithoutMakingAnEntry() {
    // When / Then
    assertThatExceptionOfType(NoSuchElementException.class)
      .isThrownBy(() -> service.changeOwned(bea, UUID.randomUUID(), true))
      .withMessage("That book isn't in the catalog anymore.");
    assertThat(repository.count()).isZero();
  }

  @Test
  void aPageOutsideTheBookIsRefused() {
    // Given
    var cabinGetaway = book("Dot by Letter", "Cabin Getaway");

    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> service.markPageDone(bea, cabinGetaway, 21))
      .withMessage("There's no page 21 in this book. Its pages go from 1 to 20.");
  }

  @Test
  void aLargeDotBookHas25Pages() {
    // Given
    var mandalas = book("Dot by Letter Large Dot", "Mandalas");

    // When
    var tracked = service.markPageDone(bea, mandalas, 25);

    // Then
    assertThat(tracked.entry().getPagesDone(tracked.pageCount())).containsExactly(25);
  }

  @Test
  void pagesAreStoredAsAnIntegerArrayAndReadBackInOrder() {
    // Given
    var cabinGetaway = book("Dot by Letter", "Cabin Getaway");
    service.markPageDone(bea, cabinGetaway, 12);
    service.markPageDone(bea, cabinGetaway, 3);
    service.markPageDone(bea, cabinGetaway, 7);
    entityManager.flush();
    entityManager.clear();

    // When
    var entry = repository.findByMemberIdAndBookId(bea, cabinGetaway).orElseThrow();

    // Then
    assertThat(entry.getPagesDone(20)).containsExactly(3, 7, 12);
    assertThat(jdbc.sql("select pages_done::text from collection_entry where id = ?").param(entry.getId())
      .query(String.class).single()).isEqualTo("{3,7,12}");
    assertThat(jdbc.sql("select data_type from information_schema.columns "
        + "where table_name = 'collection_entry' and column_name = 'pages_done'")
      .query(String.class).single()).isEqualTo("ARRAY");
  }

  @Test
  void markingTheFirstPageStartsTheBookTodayAndMarksItOwned() {
    // Given
    var cabinGetaway = book("Dot by Letter", "Cabin Getaway");

    // When
    var entry = service.markPageDone(bea, cabinGetaway, 1).entry();

    // Then
    assertThat(entry.getProgress()).isEqualTo(IN_PROGRESS);
    assertThat(entry.getStartedOn()).isEqualTo(TODAY);
    assertThat(entry.isOwned()).isTrue();
  }

  @Test
  void markingEveryPageFinishesTheBookToday() {
    // Given
    var cabinGetaway = book("Dot by Letter", "Cabin Getaway");
    IntStream.rangeClosed(1, 19).forEach(page -> service.markPageDone(bea, cabinGetaway, page));

    // When
    var entry = service.markPageDone(bea, cabinGetaway, 20).entry();

    // Then
    assertThat(entry.getProgress()).isEqualTo(FINISHED);
    assertThat(entry.getFinishedOn()).isEqualTo(TODAY);
  }

  @Test
  void markingAPageThatIsAlreadyDoneWritesNothing() {
    // Given
    var cabinGetaway = book("Dot by Letter", "Cabin Getaway");
    var id = Objects.requireNonNull(service.markPageDone(bea, cabinGetaway, 1).entry().getId());
    entityManager.flush();
    var version = version(id);

    // When
    var tracked = service.markPageDone(bea, cabinGetaway, 1);
    entityManager.flush();

    // Then
    assertThat(tracked.entry().getPagesDone(20)).containsExactly(1);
    assertThat(version(id)).isEqualTo(version);
  }

  @Test
  void ratingsAndNotesAreStoredAndReadBack() {
    // Given
    var cabinGetaway = book("Dot by Letter", "Cabin Getaway");
    service.rate(bea, cabinGetaway, 0, 5, null);
    service.changeNotes(bea, cabinGetaway, "Saving the lake page for last.");
    entityManager.flush();
    entityManager.clear();

    // When
    var entry = repository.findByMemberIdAndBookId(bea, cabinGetaway).orElseThrow();

    // Then
    assertThat(entry.getRating()).isZero();
    assertThat(entry.getDesignRating()).isEqualTo(5);
    assertThat(entry.getColorRating()).isNull();
    assertThat(entry.getNotes()).isEqualTo("Saving the lake page for last.");
  }

  @Test
  void aLoweredPageCountLeavesLaterPagesStoredButOutOfTheEntryAndItsCount() {
    // Given
    var cabinGetaway = books.findById(book("Dot by Letter", "Cabin Getaway")).orElseThrow();
    var id = Objects.requireNonNull(cabinGetaway.getId());
    IntStream.of(1, 2, 3, 19, 20).forEach(page -> service.markPageDone(bea, id, page));
    bookService.update(id, new BookDetails(cabinGetaway.getProductLineId(), cabinGetaway.getName(),
      cabinGetaway.getAvailability(), 18, cabinGetaway.getListings()));

    // When
    var tracked = service.findAll(bea).getFirst();

    // Then
    assertThat(tracked.pageCount()).isEqualTo(18);
    assertThat(tracked.entry().getPagesDone(tracked.pageCount())).containsExactly(1, 2, 3);

    // When
    IntStream.rangeClosed(4, 17).forEach(page -> service.markPageDone(bea, id, page));

    // Then
    assertThat(service.findAll(bea).getFirst().entry().getProgress()).isEqualTo(IN_PROGRESS);

    // When
    var finished = service.markPageDone(bea, id, 18);
    entityManager.flush();

    // Then
    assertThat(finished.entry().getProgress()).isEqualTo(FINISHED);
    assertThat(jdbc.sql("select pages_done::text from collection_entry where book_id = ?").param(id)
      .query(String.class).single()).isEqualTo("{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20}");
  }

  @Test
  void aBookSomeoneTracksCannotBeDeleted() {
    // Given
    var desertDreams = book("Trace by Groove", "Desert Dreams");
    service.changeWanted(bea, desertDreams, Wanted.YES);

    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> bookService.delete(desertDreams))
      .withMessage(
        "Someone in the group is already tracking this book, so it can't be deleted. Mark it as retired instead.");
    assertThat(books.findById(desertDreams)).isPresent();
  }

  @Test
  void aBookNobodyTracksCanStillBeDeleted() {
    // Given
    var desertDreams = book("Trace by Groove", "Desert Dreams");
    service.changeWanted(bea, book("Trace by Groove", "Jungle Oasis"), Wanted.YES);

    // When
    bookService.delete(desertDreams);
    entityManager.flush();

    // Then
    assertThat(books.findById(desertDreams)).isEmpty();
  }

  private long version(UUID id) {
    return jdbc.sql("select version from collection_entry where id = ?").param(id).query(Long.class).single();
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
