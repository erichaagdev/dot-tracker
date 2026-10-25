package dev.erichaag.dottracker;

import static dev.erichaag.dottracker.collection.Progress.FINISHED;
import static dev.erichaag.dottracker.collection.Progress.IN_PROGRESS;
import static dev.erichaag.dottracker.collection.Progress.NOT_STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import dev.erichaag.dottracker.book.Book;
import dev.erichaag.dottracker.book.BookRepository;
import dev.erichaag.dottracker.book.ProductLineRepository;
import dev.erichaag.dottracker.collection.CollectionEntry;
import dev.erichaag.dottracker.collection.CollectionEntryRepository;
import dev.erichaag.dottracker.collection.Wanted;
import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.member.MemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

// The seeder runs by hand inside the test's transaction, so its members and entries don't outlive the test. The clock
// reads October 25, 2026, so the mockups' "Started September 28" is 27 days earlier.
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({PostgresTestConfiguration.class, FixedClockConfiguration.class})
class DemoSeederTest {

  @Autowired
  MemberRepository members;

  @Autowired
  ProductLineRepository productLines;

  @Autowired
  BookRepository books;

  @Autowired
  CollectionEntryRepository entries;

  @Autowired
  Clock clock;

  @Test
  void anaJoinsAsAnAdminAndBeaAsAMember() {
    // When
    seed();

    // Then
    assertThat(members.findAllByOrderByDisplayNameAsc())
      .extracting(Member::getEmail, Member::getDisplayName, Member::getRole)
      .containsExactly(
        tuple("ana@example.com", "Ana", Role.ADMIN),
        tuple("bea@example.com", "Bea", Role.MEMBER)
      );
  }

  @Test
  void aDatabaseThatAlreadyHasMembersIsLeftAsItIs() {
    // Given
    members.save(new Member("cat@example.com", "Cat", Role.ADMIN, FixedClockConfiguration.NOW));

    // When
    seed();

    // Then
    assertThat(members.findAll()).extracting(Member::getEmail).containsExactly("cat@example.com");
    assertThat(entries.count()).isZero();
  }

  @Test
  void anaIsWorkingOnTheBooksTheMockupsShow() {
    // When
    seed();

    // Then
    var collection = collectionOf("ana@example.com");
    var cabinGetaway = entry(collection, "Cabin Getaway");
    assertThat(cabinGetaway.getProgress()).isEqualTo(IN_PROGRESS);
    assertThat(cabinGetaway.isOwned()).isTrue();
    assertThat(cabinGetaway.getPagesDone(20)).isEqualTo(pages(1, 15));
    assertThat(cabinGetaway.getStartedOn()).isEqualTo(LocalDate.parse("2026-09-28"));
    assertThat(cabinGetaway.getRating()).isEqualTo(4);
    assertThat(cabinGetaway.getDesignRating()).isEqualTo(5);
    assertThat(cabinGetaway.getColorRating()).isNull();
    assertThat(cabinGetaway.getNotes())
      .isEqualTo("Using the fine tips for the snow on the roof. Saving the lake page for last.");
    var cornerCafe = entry(collection, "Corner Cafe");
    assertThat(cornerCafe.getProgress()).isEqualTo(IN_PROGRESS);
    assertThat(cornerCafe.getPagesDone(20)).containsExactly(1, 2, 3, 4, 5, 7, 8, 10, 12);
    assertThat(cornerCafe.getRating()).isEqualTo(5);
    assertThat(cornerCafe.getDesignRating()).isEqualTo(5);
    assertThat(cornerCafe.getColorRating()).isEqualTo(4);
    var cottageGarden = entry(collection, "Cottage Garden");
    assertThat(cottageGarden.getProgress()).isEqualTo(IN_PROGRESS);
    assertThat(cottageGarden.getPagesDone(20)).hasSize(6);
    assertThat(collection.values()).filteredOn(entry -> entry.getProgress() == IN_PROGRESS).hasSize(3);
  }

  @Test
  void anaRatedTheBooksSheFinishedAndTheShelfShowsTheMockupsTwoAsTheMostRecent() {
    // When
    seed();

    // Then
    var finished = collectionOf("ana@example.com").entrySet().stream()
      .filter(entry -> entry.getValue().getProgress() == FINISHED)
      .sorted(Comparator.comparing((Map.Entry<String, CollectionEntry> entry) ->
        Objects.requireNonNull(entry.getValue().getFinishedOn())).reversed())
      .toList();
    assertThat(finished).hasSizeGreaterThanOrEqualTo(6);
    assertThat(finished).allSatisfy(entry -> {
      assertThat(entry.getValue().getPagesDone(20)).hasSize(20);
      assertThat(entry.getValue().getRating()).isNotNull();
    });
    assertThat(finished).extracting(Map.Entry::getKey, entry -> entry.getValue().getRating())
      .startsWith(tuple("Gingerbread Delight", 5), tuple("Home For The Holidays", 4));
    assertThat(finished).anySatisfy(entry -> assertThat(entry.getValue().getDesignRating()).isNotNull());
    assertThat(finished).anySatisfy(entry -> {
      assertThat(entry.getValue().getDesignRating()).isNull();
      assertThat(entry.getValue().getColorRating()).isNull();
    });
  }

  @Test
  void anaOwnsCozyCompanionsWithoutStartingItAndHasAWishlist() {
    // When
    seed();

    // Then
    var collection = collectionOf("ana@example.com");
    var cozyCompanions = entry(collection, "Cozy Companions");
    assertThat(cozyCompanions.isOwned()).isTrue();
    assertThat(cozyCompanions.getProgress()).isEqualTo(NOT_STARTED);
    var wishlist = collection.entrySet().stream()
      .filter(entry -> isOnWishlist(entry.getValue()))
      .map(Map.Entry::getKey)
      .toList();
    assertThat(wishlist).contains("Holiday Helpers", "Coastal Calm").hasSizeBetween(3, 9);
    assertThat(collection.values()).extracting(CollectionEntry::getWanted).contains(Wanted.YES, Wanted.MAYBE);
  }

  @Test
  void beasCollectionIsSmallerAndHasABookInProgressRatingsAndAWishlist() {
    // When
    seed();

    // Then
    var ana = collectionOf("ana@example.com");
    var bea = collectionOf("bea@example.com");
    assertThat(bea).isNotEmpty().hasSizeLessThan(ana.size());
    assertThat(bea.values()).anySatisfy(entry -> assertThat(entry.getProgress()).isEqualTo(IN_PROGRESS));
    assertThat(bea.values()).anySatisfy(entry -> {
      assertThat(entry.getProgress()).isEqualTo(FINISHED);
      assertThat(entry.getRating()).isNotNull();
    });
    assertThat(bea.values()).anySatisfy(entry -> assertThat(isOnWishlist(entry)).isTrue());
    assertThat(bea.keySet()).isNotEqualTo(ana.keySet());
  }

  @Test
  void collectionsAreSeededForDemoMembersWhoHaveNone() {
    // Given
    members.saveAll(List.of(
      new Member("ana@example.com", "Ana", Role.ADMIN, FixedClockConfiguration.NOW),
      new Member("bea@example.com", "Bea", Role.MEMBER, FixedClockConfiguration.NOW)
    ));

    // When
    seed();

    // Then
    assertThat(collectionOf("ana@example.com")).containsKey("Cabin Getaway");
    assertThat(collectionOf("bea@example.com")).isNotEmpty();
  }

  @Test
  void aDatabaseThatAlreadyHasEntriesKeepsItsCollections() {
    // Given
    seed();
    var ana = collectionOf("ana@example.com").size();
    var bea = collectionOf("bea@example.com").size();

    // When
    seed();

    // Then
    assertThat(entries.count()).isEqualTo(ana + bea);
  }

  private void seed() {
    new DemoSeeder(members, productLines, books, entries, clock).run(new DefaultApplicationArguments());
  }

  /** A member's entries by book name. No demo member tracks two books of the same name. */
  private Map<String, CollectionEntry> collectionOf(String email) {
    var memberId = Objects.requireNonNull(members.findByEmail(email).orElseThrow().getId());
    var names = books.findAll().stream()
      .collect(Collectors.toMap(book -> Objects.requireNonNull(book.getId()), Book::getName));
    return entries.findByMemberId(memberId).stream()
      .collect(Collectors.toMap(entry -> Objects.requireNonNull(names.get(entry.getBookId())), Function.identity()));
  }

  private static CollectionEntry entry(Map<String, CollectionEntry> collection, String name) {
    return Objects.requireNonNull(collection.get(name), name);
  }

  private static boolean isOnWishlist(CollectionEntry entry) {
    return !entry.isOwned() && (entry.getWanted() == Wanted.YES || entry.getWanted() == Wanted.MAYBE);
  }

  private static List<Integer> pages(int first, int last) {
    return IntStream.rangeClosed(first, last).boxed().toList();
  }
}
