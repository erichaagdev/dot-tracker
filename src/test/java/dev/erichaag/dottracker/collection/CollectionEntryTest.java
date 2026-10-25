package dev.erichaag.dottracker.collection;

import static dev.erichaag.dottracker.collection.Progress.FINISHED;
import static dev.erichaag.dottracker.collection.Progress.IN_PROGRESS;
import static dev.erichaag.dottracker.collection.Progress.NOT_STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CollectionEntryTest {

  private static final UUID BEA = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3e");

  private static final UUID CABIN_GETAWAY = UUID.fromString("0199a3c2-7c1e-7d4b-9a50-6f1f0e2b8c3f");

  private static final int PAGES = 20;

  private static final Instant CREATED = Instant.parse("2026-09-28T15:00:00Z");

  private static final Instant NOW = Instant.parse("2026-10-25T17:00:00Z");

  private static final LocalDate TODAY = LocalDate.parse("2026-10-25");

  @Test
  void aNewEntryIsNotStartedNotOwnedAndHasNothingElse() {
    // When
    var entry = entry();

    // Then
    assertThat(entry.getMemberId()).isEqualTo(BEA);
    assertThat(entry.getBookId()).isEqualTo(CABIN_GETAWAY);
    assertThat(entry.isOwned()).isFalse();
    assertThat(entry.getWanted()).isNull();
    assertThat(entry.getProgress()).isEqualTo(NOT_STARTED);
    assertThat(entry.getPagesDone(PAGES)).isEmpty();
    assertThat(entry.getRating()).isNull();
    assertThat(entry.getNotes()).isNull();
    assertThat(entry.getStartedOn()).isNull();
    assertThat(entry.getFinishedOn()).isNull();
    assertThat(entry.getUpdatedAt()).isEqualTo(CREATED);
  }

  @Test
  void markingTheFirstPageStartsTheBookAndMarksItOwned() {
    // Given
    var entry = entry();

    // When
    entry.markPageDone(7, PAGES, now());

    // Then
    assertThat(entry.getPagesDone(PAGES)).containsExactly(7);
    assertThat(entry.getProgress()).isEqualTo(IN_PROGRESS);
    assertThat(entry.getStartedOn()).isEqualTo(TODAY);
    assertThat(entry.isOwned()).isTrue();
    assertThat(entry.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  void markingAnotherPageKeepsTheDayTheBookWasStarted() {
    // Given
    var entry = entry();
    entry.markPageDone(1, PAGES, at("2026-09-28T15:00:00Z"));

    // When
    entry.markPageDone(2, PAGES, now());

    // Then
    assertThat(entry.getStartedOn()).isEqualTo(LocalDate.parse("2026-09-28"));
    assertThat(entry.getProgress()).isEqualTo(IN_PROGRESS);
  }

  @Test
  void markingTheLastPageFinishesTheBook() {
    // Given
    var entry = withPagesDone(pages(1, 19));

    // When
    entry.markPageDone(20, PAGES, now());

    // Then
    assertThat(entry.getProgress()).isEqualTo(FINISHED);
    assertThat(entry.getFinishedOn()).isEqualTo(TODAY);
    assertThat(entry.getStartedOn()).isEqualTo(LocalDate.parse("2026-09-28"));
    assertThat(entry.isOwned()).isTrue();
  }

  @Test
  void theLastRemainingPageCanBeAnyPage() {
    // Given
    var entry = withPagesDone(pages(2, 20));

    // When
    entry.markPageDone(1, PAGES, now());

    // Then
    assertThat(entry.getProgress()).isEqualTo(FINISHED);
  }

  @Test
  void aOnePageBookIsStartedAndFinishedByItsOnlyPage() {
    // Given
    var entry = entry();

    // When
    entry.markPageDone(1, 1, now());

    // Then
    assertThat(entry.getProgress()).isEqualTo(FINISHED);
    assertThat(entry.getStartedOn()).isEqualTo(TODAY);
    assertThat(entry.getFinishedOn()).isEqualTo(TODAY);
  }

  @Test
  void markingAPageThatIsAlreadyDoneChangesNothing() {
    // Given
    var entry = withPagesDone(List.of(1, 2));
    var updatedAt = entry.getUpdatedAt();

    // When
    entry.markPageDone(2, PAGES, now());

    // Then
    assertThat(entry.getPagesDone(PAGES)).containsExactly(1, 2);
    assertThat(entry.getUpdatedAt()).isEqualTo(updatedAt);
  }

  @Test
  void pagesAreKeptInOrder() {
    // Given
    var entry = entry();

    // When
    entry.markPageDone(12, PAGES, now());
    entry.markPageDone(3, PAGES, now());
    entry.markPageDone(20, PAGES, now());
    entry.markPageDone(7, PAGES, now());

    // Then
    assertThat(entry.getPagesDone(PAGES)).containsExactly(3, 7, 12, 20);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 21})
  void aPageOutsideTheBookIsRefusedAndChangesNothing(int page) {
    // Given
    var entry = withPagesDone(List.of(1, 2));

    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> entry.markPageDone(page, PAGES, now()))
      .withMessage("There's no page " + page + " in this book. Its pages go from 1 to 20.");
    assertThatIllegalArgumentException().isThrownBy(() -> entry.markPageNotDone(page, PAGES, now()));
    assertThat(entry.getPagesDone(PAGES)).containsExactly(1, 2);
  }

  @Test
  void unmarkingAPageRemovesIt() {
    // Given
    var entry = withPagesDone(List.of(1, 2, 3));

    // When
    entry.markPageNotDone(2, PAGES, now());

    // Then
    assertThat(entry.getPagesDone(PAGES)).containsExactly(1, 3);
    assertThat(entry.getProgress()).isEqualTo(IN_PROGRESS);
    assertThat(entry.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  void unmarkingTheOnlyPageDoneKeepsTheBookInProgress() {
    // Given
    var entry = withPagesDone(List.of(1));

    // When
    entry.markPageNotDone(1, PAGES, now());

    // Then
    assertThat(entry.getPagesDone(PAGES)).isEmpty();
    assertThat(entry.getProgress()).isEqualTo(IN_PROGRESS);
  }

  @Test
  void unmarkingAPageThatIsNotDoneChangesNothing() {
    // Given
    var entry = withPagesDone(List.of(1, 2));
    var updatedAt = entry.getUpdatedAt();

    // When
    entry.markPageNotDone(5, PAGES, now());

    // Then
    assertThat(entry.getPagesDone(PAGES)).containsExactly(1, 2);
    assertThat(entry.getUpdatedAt()).isEqualTo(updatedAt);
  }

  @Test
  void unmarkingAPageOfAFinishedBookMovesItBackToInProgress() {
    // Given
    var entry = withPagesDone(pages(1, 20));

    // When
    entry.markPageNotDone(14, PAGES, now());

    // Then
    assertThat(entry.getProgress()).isEqualTo(IN_PROGRESS);
    assertThat(entry.getFinishedOn()).isNull();
    assertThat(entry.getStartedOn()).isEqualTo(LocalDate.parse("2026-09-28"));
  }

  @Test
  void settingProgressByHandNeverChangesPages() {
    // Given
    var entry = withPagesDone(List.of(1, 2, 3));

    // When
    entry.changeProgress(FINISHED, now());

    // Then
    assertThat(entry.getProgress()).isEqualTo(FINISHED);
    assertThat(entry.getPagesDone(PAGES)).containsExactly(1, 2, 3);

    // When
    entry.changeProgress(NOT_STARTED, now());

    // Then
    assertThat(entry.getProgress()).isEqualTo(NOT_STARTED);
    assertThat(entry.getPagesDone(PAGES)).containsExactly(1, 2, 3);
  }

  @ParameterizedTest
  @EnumSource(value = Progress.class, names = {"IN_PROGRESS", "FINISHED"})
  void markingABookInProgressOrFinishedMarksItOwned(Progress progress) {
    // Given
    var entry = entry();

    // When
    entry.changeProgress(progress, now());

    // Then
    assertThat(entry.isOwned()).isTrue();
  }

  @Test
  void startingABookByHandRecordsTodayAsItsStart() {
    // Given
    var entry = entry();

    // When
    entry.changeProgress(IN_PROGRESS, now());

    // Then
    assertThat(entry.getStartedOn()).isEqualTo(TODAY);
    assertThat(entry.getFinishedOn()).isNull();
  }

  @Test
  void finishingABookByHandRecordsTodayAsItsFinishAndLeavesItsStartAlone() {
    // Given
    var entry = entry();

    // When
    entry.changeProgress(FINISHED, now());

    // Then
    assertThat(entry.getFinishedOn()).isEqualTo(TODAY);
    assertThat(entry.getStartedOn()).isNull();
  }

  @Test
  void movingAFinishedBookBackToInProgressByHandClearsItsFinishAndKeepsItsStart() {
    // Given
    var entry = withPagesDone(pages(1, 20));

    // When
    entry.changeProgress(IN_PROGRESS, now());

    // Then
    assertThat(entry.getStartedOn()).isEqualTo(LocalDate.parse("2026-09-28"));
    assertThat(entry.getFinishedOn()).isNull();
  }

  @Test
  void movingABookBackToNotStartedClearsBothDates() {
    // Given
    var entry = withPagesDone(pages(1, 20));

    // When
    entry.changeProgress(NOT_STARTED, now());

    // Then
    assertThat(entry.getStartedOn()).isNull();
    assertThat(entry.getFinishedOn()).isNull();
  }

  @Test
  void settingTheSameProgressAgainChangesNothing() {
    // Given
    var entry = withPagesDone(pages(1, 20));
    var updatedAt = entry.getUpdatedAt();

    // When
    entry.changeProgress(FINISHED, now());

    // Then
    assertThat(entry.getFinishedOn()).isEqualTo(LocalDate.parse("2026-10-01"));
    assertThat(entry.getUpdatedAt()).isEqualTo(updatedAt);
  }

  @Test
  void markingPagesOfABookFinishedByHandKeepsItFinished() {
    // Given
    var entry = entry();
    entry.changeProgress(FINISHED, at("2026-10-01T15:00:00Z"));

    // When
    entry.markPageDone(3, PAGES, now());

    // Then
    assertThat(entry.getProgress()).isEqualTo(FINISHED);
    assertThat(entry.getFinishedOn()).isEqualTo(LocalDate.parse("2026-10-01"));
  }

  @Test
  void unmarkingOwnedLeavesProgressAlone() {
    // Given
    var entry = withPagesDone(pages(1, 20));

    // When
    entry.changeOwned(false, now());

    // Then
    assertThat(entry.isOwned()).isFalse();
    assertThat(entry.getProgress()).isEqualTo(FINISHED);
    assertThat(entry.getPagesDone(PAGES)).hasSize(20);
  }

  @Test
  void markingOwnedAgainChangesNothing() {
    // Given
    var entry = entry();
    entry.changeOwned(true, at("2026-10-01T15:00:00Z"));

    // When
    entry.changeOwned(true, now());

    // Then
    assertThat(entry.getUpdatedAt()).isEqualTo(Instant.parse("2026-10-01T15:00:00Z"));
  }

  @Test
  void wantedIsIndependentOfOwned() {
    // Given
    var entry = entry();
    entry.changeOwned(true, now());

    // When
    entry.changeWanted(Wanted.MAYBE, now());

    // Then
    assertThat(entry.getWanted()).isEqualTo(Wanted.MAYBE);
    assertThat(entry.isOwned()).isTrue();

    // When
    entry.changeWanted(null, now());

    // Then
    assertThat(entry.getWanted()).isNull();
    assertThat(entry.isOwned()).isTrue();
  }

  @Test
  void ratingsAreZeroToFiveOrNone() {
    // Given
    var entry = entry();

    // When
    entry.rate(0, 5, null, now());

    // Then
    assertThat(entry.getRating()).isZero();
    assertThat(entry.getDesignRating()).isEqualTo(5);
    assertThat(entry.getColorRating()).isNull();
    assertThat(entry.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  void aNullRatingClearsIt() {
    // Given
    var entry = entry();
    entry.rate(4, 5, 3, now());

    // When
    entry.rate(4, null, 3, now());

    // Then
    assertThat(entry.getRating()).isEqualTo(4);
    assertThat(entry.getDesignRating()).isNull();
    assertThat(entry.getColorRating()).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 6})
  void aRatingOutsideZeroToFiveIsRefusedAndChangesNothing(int rating) {
    // Given
    var entry = entry();
    entry.rate(4, 5, 3, now());

    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> entry.rate(2, 2, rating, now()))
      .withMessage("A rating must be between 0 and 5.");
    assertThat(entry.getRating()).isEqualTo(4);
    assertThat(entry.getDesignRating()).isEqualTo(5);
    assertThat(entry.getColorRating()).isEqualTo(3);
  }

  @Test
  void notesAreTrimmed() {
    // Given
    var entry = entry();

    // When
    entry.changeNotes("  Saving the lake page for last.\n", now());

    // Then
    assertThat(entry.getNotes()).isEqualTo("Saving the lake page for last.");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  \n"})
  void blankNotesAreNoNotes(@Nullable String notes) {
    // Given
    var entry = entry();
    entry.changeNotes("Saving the lake page for last.", now());

    // When
    entry.changeNotes(notes, now());

    // Then
    assertThat(entry.getNotes()).isNull();
  }

  @Test
  void pagesBeyondALoweredPageCountStayStoredButDoNotCount() {
    // Given
    var entry = withPagesDone(List.of(1, 2, 19, 20));

    // When / Then
    assertThat(entry.getPagesDone(18)).containsExactly(1, 2);
    assertThat(entry.getPagesDone(20)).containsExactly(1, 2, 19, 20);
  }

  @Test
  void theLastRemainingPageCountsOnlyPagesWithinTheBook() {
    // Given 17 pages done of a book of 20, whose page count is then lowered to 18
    var entry = withPagesDone(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 19, 20));

    // When
    entry.markPageDone(16, 18, now());

    // Then
    assertThat(entry.getProgress()).isEqualTo(IN_PROGRESS);

    // When
    entry.markPageDone(17, 18, now());
    entry.markPageDone(18, 18, now());

    // Then
    assertThat(entry.getProgress()).isEqualTo(FINISHED);
    assertThat(entry.getPagesDone(18)).isEqualTo(pages(1, 18));
  }

  @Test
  void todayIsTheDateInTheClocksTimeZone() {
    // Given
    var entry = entry();
    var lateEveningInChicago = Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), ZoneId.of("America/Chicago"));

    // When
    entry.markPageDone(1, PAGES, lateEveningInChicago);

    // Then
    assertThat(entry.getStartedOn()).isEqualTo(TODAY);
  }

  private static CollectionEntry entry() {
    return new CollectionEntry(BEA, CABIN_GETAWAY, Clock.fixed(CREATED, ZoneId.of("America/Chicago")));
  }

  /** An entry started on September 28 with these pages done, the rest of them on October 1. */
  private static CollectionEntry withPagesDone(List<Integer> pages) {
    var entry = entry();
    entry.markPageDone(pages.getFirst(), PAGES, at("2026-09-28T15:00:00Z"));
    pages.forEach(page -> entry.markPageDone(page, PAGES, at("2026-10-01T15:00:00Z")));
    return entry;
  }

  private static List<Integer> pages(int first, int last) {
    return IntStream.rangeClosed(first, last).boxed().toList();
  }

  private static Clock now() {
    return Clock.fixed(NOW, ZoneId.of("America/Chicago"));
  }

  private static Clock at(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneId.of("America/Chicago"));
  }
}
