package dev.erichaag.dottracker.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BookTest {

  private static final UUID DOT_BY_LETTER = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c40");

  private static final UUID TRACE_BY_LINE = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c41");

  private static final Instant ADDED = Instant.parse("2026-10-01T15:30:00Z");

  private static final Instant CHANGED = Instant.parse("2026-10-25T17:00:00Z");

  @Test
  void theNameIsTrimmed() {
    // When
    var book = book("  Cabin Getaway ", List.of(), List.of());

    // Then
    assertThat(book.getName()).isEqualTo("Cabin Getaway");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void aBlankNameIsRefused(String name) {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> book(name, List.of(), List.of()))
      .withMessage("A book needs a name.");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void aBookNeedsAtLeastOnePage(int pageCount) {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> new Book(DOT_BY_LETTER, "Cabin Getaway", List.of(), Availability.AVAILABLE, pageCount,
        List.of(), ADDED))
      .withMessage("A book needs at least one page.");
  }

  @Test
  void otherNamesAreTrimmedAndKeptOnceIgnoringCase() {
    // When
    var book = book("Lovestruck", List.of(" Made with Love ", "made with love", "Lovestruck/Made with Love"),
      List.of());

    // Then
    assertThat(book.getAlsoKnownAs()).containsExactly("Made with Love", "Lovestruck/Made with Love");
  }

  @Test
  void blankOtherNamesAndTheBooksOwnNameAreLeftOut() {
    // When
    var book = book("Snowflake Serenity", List.of(" ", "snowflake serenity", "Snowfall Serenity"), List.of());

    // Then
    assertThat(book.getAlsoKnownAs()).containsExactly("Snowfall Serenity");
  }

  @Test
  void aBookWithAnExclusiveListingIsAStoreExclusive() {
    // When
    var book = book("Cabin Getaway", List.of(), List.of(turnerToys(), new Listing("Target", null, null, true)));

    // Then
    assertThat(book.isExclusive()).isTrue();
  }

  @Test
  void aBookWithoutAnExclusiveListingIsNotAStoreExclusive() {
    // When
    var book = book("Winter's Glow", List.of(), List.of(turnerToys()));

    // Then
    assertThat(book.isExclusive()).isFalse();
  }

  @Test
  void aNewBookWasAddedAndLastChangedAtTheSameTime() {
    // When
    var book = book("Cabin Getaway", List.of(), List.of());

    // Then
    assertThat(book.getCreatedAt()).isEqualTo(ADDED);
    assertThat(book.getUpdatedAt()).isEqualTo(ADDED);
  }

  @Test
  void updatingABookChangesEverythingAndWhenItWasLastChanged() {
    // Given
    var book = book("Cabin Getaway", List.of("Cabin Retreat"), List.of(turnerToys()));
    var target = new Listing("Target", 1299, "https://www.target.com/", true);

    // When
    book.update(TRACE_BY_LINE, " Fall Harvest ", List.of("Harvest"), Availability.RETIRED, 24, List.of(target),
      CHANGED);

    // Then
    assertThat(book.getProductLineId()).isEqualTo(TRACE_BY_LINE);
    assertThat(book.getName()).isEqualTo("Fall Harvest");
    assertThat(book.getAlsoKnownAs()).containsExactly("Harvest");
    assertThat(book.getAvailability()).isEqualTo(Availability.RETIRED);
    assertThat(book.getPageCount()).isEqualTo(24);
    assertThat(book.getListings()).containsExactly(target);
    assertThat(book.getCreatedAt()).isEqualTo(ADDED);
    assertThat(book.getUpdatedAt()).isEqualTo(CHANGED);
  }

  @Test
  void aRefusedUpdateLeavesTheBookAsItWas() {
    // Given
    var book = book("Cabin Getaway", List.of(), List.of(turnerToys()));

    // When / Then
    assertThatIllegalArgumentException().isThrownBy(() ->
      book.update(TRACE_BY_LINE, "Fall Harvest", List.of(), Availability.RETIRED, 0, List.of(), CHANGED));
    assertThat(book.getProductLineId()).isEqualTo(DOT_BY_LETTER);
    assertThat(book.getName()).isEqualTo("Cabin Getaway");
    assertThat(book.getListings()).containsExactly(turnerToys());
    assertThat(book.getUpdatedAt()).isEqualTo(ADDED);
  }

  private static Book book(String name, List<String> alsoKnownAs, List<Listing> listings) {
    return new Book(DOT_BY_LETTER, name, alsoKnownAs, Availability.AVAILABLE, 20, listings, ADDED);
  }

  private static Listing turnerToys() {
    return new Listing("Turner Toys", 1499, null, false);
  }
}
