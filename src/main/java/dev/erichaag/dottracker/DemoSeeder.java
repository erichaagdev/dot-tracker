package dev.erichaag.dottracker;

import static dev.erichaag.dottracker.collection.Wanted.MAYBE;
import static dev.erichaag.dottracker.collection.Wanted.YES;

import dev.erichaag.dottracker.book.Book;
import dev.erichaag.dottracker.book.BookRepository;
import dev.erichaag.dottracker.book.ProductLine;
import dev.erichaag.dottracker.book.ProductLineRepository;
import dev.erichaag.dottracker.collection.CollectionEntry;
import dev.erichaag.dottracker.collection.CollectionEntryRepository;
import dev.erichaag.dottracker.collection.Wanted;
import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.member.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds the demo profile's members, Ana as an admin and Bea as a member, who sign in with the password "demo", and
 * gives each of them a collection. Ana's is the one the mockups show, and Bea's is smaller. A database that already
 * has members keeps them, and one that already has collection entries keeps those.
 */
@Component
@Profile("demo")
public class DemoSeeder implements ApplicationRunner {

  private static final String DOT_BY_LETTER = "Dot by Letter";

  private static final String MINI = "Dot by Letter Mini";

  private static final String TRACE_BY_LINE = "Trace by Line";

  private final MemberRepository members;

  private final ProductLineRepository productLines;

  private final BookRepository books;

  private final CollectionEntryRepository entries;

  private final Clock clock;

  public DemoSeeder(MemberRepository members, ProductLineRepository productLines, BookRepository books,
    CollectionEntryRepository entries, Clock clock) {
    this.members = members;
    this.productLines = productLines;
    this.books = books;
    this.entries = entries;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (members.count() == 0) {
      var now = clock.instant();
      members.saveAll(List.of(
        new Member("ana@example.com", "Ana", Role.ADMIN, now),
        new Member("bea@example.com", "Bea", Role.MEMBER, now)
      ));
    }
    if (entries.count() == 0) {
      members.findByEmail("ana@example.com").ifPresent(ana -> entries.saveAll(anasCollection(id(ana))));
      members.findByEmail("bea@example.com").ifPresent(bea -> entries.saveAll(beasCollection(id(bea))));
    }
  }

  private List<CollectionEntry> anasCollection(UUID ana) {
    var cabinGetaway = working(ana, DOT_BY_LETTER, "Cabin Getaway", 27, pages(1, 15));
    cabinGetaway.rate(4, 5, null, clock);
    cabinGetaway.changeNotes("Using the fine tips for the snow on the roof. Saving the lake page for last.", clock);
    var cornerCafe = working(ana, DOT_BY_LETTER, "Corner Cafe", 12, List.of(1, 2, 3, 4, 5, 7, 8, 10, 12));
    cornerCafe.rate(5, 5, 4, clock);
    var gingerbreadDelight = finished(ana, TRACE_BY_LINE, "Gingerbread Delight", 30, 2);
    gingerbreadDelight.rate(5, 4, 5, clock);
    var homeForTheHolidays = finished(ana, "Color to Reveal Scented", "Home For The Holidays", 35, 9);
    homeForTheHolidays.rate(4, null, null, clock);
    var americana = finished(ana, DOT_BY_LETTER, "Americana", 120, 95);
    americana.rate(5, 5, 5, clock);
    var botanicals = finished(ana, DOT_BY_LETTER, "Botanicals", 90, 70);
    botanicals.rate(4, null, null, clock);
    var landscapes = finished(ana, DOT_BY_LETTER, "Landscapes", 70, 50);
    landscapes.rate(4, 3, 5, clock);
    var cozyCottage = finished(ana, DOT_BY_LETTER, "Cozy Cottage", 60, 40);
    cozyCottage.rate(3, null, null, clock);
    var cafeComforts = finished(ana, MINI, "Cafe Comforts", 45, 30);
    cafeComforts.rate(4, null, null, clock);
    var enchantedMagic = finished(ana, MINI, "Enchanted Magic", 40, 20);
    enchantedMagic.rate(5, 4, 4, clock);
    return List.of(
      cabinGetaway,
      cornerCafe,
      working(ana, MINI, "Cottage Garden", 5, pages(1, 6)),
      gingerbreadDelight,
      homeForTheHolidays,
      americana,
      botanicals,
      landscapes,
      cozyCottage,
      cafeComforts,
      enchantedMagic,
      owned(ana, DOT_BY_LETTER, "Cozy Companions"),
      wanted(ana, DOT_BY_LETTER, "Holiday Helpers", YES),
      wanted(ana, MINI, "Coastal Calm", YES),
      wanted(ana, "Dot by Letter Metallic", "Into the Galaxy", YES),
      wanted(ana, MINI, "Frosted Village", MAYBE),
      wanted(ana, TRACE_BY_LINE, "Fall in the Forest", MAYBE)
    );
  }

  private List<CollectionEntry> beasCollection(UUID bea) {
    var prettyPetals = finished(bea, MINI, "Pretty Petals", 40, 25);
    prettyPetals.rate(5, 4, 5, clock);
    var sweaterWeather = finished(bea, DOT_BY_LETTER, "Sweater Weather", 25, 6);
    sweaterWeather.rate(3, null, null, clock);
    return List.of(
      working(bea, TRACE_BY_LINE, "Brunch in Bloom", 8, List.of(1, 2, 3, 4, 6, 7)),
      prettyPetals,
      sweaterWeather,
      owned(bea, DOT_BY_LETTER, "Snowed In"),
      wanted(bea, "Trace by Groove", "Desert Dreams", YES),
      wanted(bea, DOT_BY_LETTER, "Cabin Getaway", MAYBE)
    );
  }

  /** A book started some days ago, with the first of its pages done that day and the rest since. */
  private CollectionEntry working(UUID member, String line, String name, int startedDaysAgo, List<Integer> pages) {
    var book = book(line, name);
    var entry = new CollectionEntry(member, id(book), daysAgo(startedDaysAgo));
    entry.markPageDone(pages.getFirst(), book.getPageCount(), daysAgo(startedDaysAgo));
    pages.forEach(page -> entry.markPageDone(page, book.getPageCount(), daysAgo(1)));
    return entry;
  }

  /** A book with every page done, the first on the day it was started and the last on the day it was finished. */
  private CollectionEntry finished(UUID member, String line, String name, int startedDaysAgo, int finishedDaysAgo) {
    var book = book(line, name);
    var entry = new CollectionEntry(member, id(book), daysAgo(startedDaysAgo));
    entry.markPageDone(1, book.getPageCount(), daysAgo(startedDaysAgo));
    pages(2, book.getPageCount()).forEach(page ->
      entry.markPageDone(page, book.getPageCount(), daysAgo(finishedDaysAgo)));
    return entry;
  }

  private CollectionEntry owned(UUID member, String line, String name) {
    var entry = new CollectionEntry(member, id(book(line, name)), clock);
    entry.changeOwned(true, clock);
    return entry;
  }

  private CollectionEntry wanted(UUID member, String line, String name, Wanted wanted) {
    var entry = new CollectionEntry(member, id(book(line, name)), clock);
    entry.changeWanted(wanted, clock);
    return entry;
  }

  private Book book(String line, String name) {
    var lineId = productLines.findByNameIgnoreCase(line).map(DemoSeeder::id).orElseThrow();
    return books.findByProductLineIdAndNameIgnoreCase(lineId, name).orElseThrow();
  }

  private Clock daysAgo(int days) {
    return Clock.offset(clock, Duration.ofDays(-days));
  }

  private static List<Integer> pages(int first, int last) {
    return IntStream.rangeClosed(first, last).boxed().toList();
  }

  private static UUID id(Member member) {
    return Objects.requireNonNull(member.getId());
  }

  private static UUID id(Book book) {
    return Objects.requireNonNull(book.getId());
  }

  private static UUID id(ProductLine line) {
    return Objects.requireNonNull(line.getId());
  }
}
