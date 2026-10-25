package dev.erichaag.dottracker.collection;

import static dev.erichaag.dottracker.collection.Progress.FINISHED;
import static dev.erichaag.dottracker.collection.Progress.IN_PROGRESS;
import static dev.erichaag.dottracker.collection.Progress.NOT_STARTED;
import static org.hibernate.annotations.UuidGenerator.Style.VERSION_7;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * One member's record of one book: whether they own it or want it, how far along they are, which pages they've
 * finished, how they rated it and their notes.
 *
 * <p>Marking a book in progress or finished also marks it owned, and unmarking owned leaves progress alone. Marking a
 * page done on a book that hasn't been started moves it to in progress, and marking the last remaining page done
 * finishes it. Unmarking a page of a finished book moves it back to in progress. Setting progress by hand never
 * changes pages. Pages are kept sorted and once each, and a page must be between 1 and the book's page count. Pages
 * beyond a page count that was lowered later stay stored but don't count. Ratings are 0 to 5, or null for not rated.
 *
 * <p>A change that leaves everything as it was changes nothing, not even when the entry was last changed, so a retried
 * request does no harm.
 */
@Entity
public class CollectionEntry {

  @Id
  @UuidGenerator(style = VERSION_7)
  private @Nullable UUID id;

  private UUID memberId = new UUID(0, 0);

  private UUID bookId = new UUID(0, 0);

  private boolean owned;

  @Enumerated(EnumType.STRING)
  private @Nullable Wanted wanted;

  @Enumerated(EnumType.STRING)
  private Progress progress = NOT_STARTED;

  // Hibernate maps a List of a basic type to an array column, here integer[]
  private List<Integer> pagesDone = List.of();

  private @Nullable Integer rating;

  private @Nullable Integer designRating;

  private @Nullable Integer colorRating;

  private @Nullable String notes;

  private @Nullable LocalDate startedOn;

  private @Nullable LocalDate finishedOn;

  private Instant updatedAt = Instant.EPOCH;

  @Version
  private @Nullable Long version;

  protected CollectionEntry() {
  }

  public CollectionEntry(UUID memberId, UUID bookId, Clock clock) {
    this.memberId = memberId;
    this.bookId = bookId;
    this.updatedAt = clock.instant();
  }

  public @Nullable UUID getId() {
    return id;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public UUID getBookId() {
    return bookId;
  }

  public boolean isOwned() {
    return owned;
  }

  public @Nullable Wanted getWanted() {
    return wanted;
  }

  public Progress getProgress() {
    return progress;
  }

  /** The pages done, in order, leaving out any beyond the book's page count. */
  public List<Integer> getPagesDone(int pageCount) {
    return pagesDone.stream().filter(page -> page <= pageCount).toList();
  }

  /** The overall rating, which is the one lists and the shelf show. */
  public @Nullable Integer getRating() {
    return rating;
  }

  public @Nullable Integer getDesignRating() {
    return designRating;
  }

  public @Nullable Integer getColorRating() {
    return colorRating;
  }

  public @Nullable String getNotes() {
    return notes;
  }

  public @Nullable LocalDate getStartedOn() {
    return startedOn;
  }

  public @Nullable LocalDate getFinishedOn() {
    return finishedOn;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /** Marks the book owned or not. Unmarking it leaves progress alone, so a book given away keeps its history. */
  public void changeOwned(boolean owned, Clock clock) {
    if (this.owned == owned) {
      return;
    }
    this.owned = owned;
    updatedAt = clock.instant();
  }

  /** Records whether the book is wanted, independent of whether it's owned. Null means no answer. */
  public void changeWanted(@Nullable Wanted wanted, Clock clock) {
    if (this.wanted == wanted) {
      return;
    }
    this.wanted = wanted;
    updatedAt = clock.instant();
  }

  /**
   * Sets progress by hand, leaving pages as they are, so a book can be finished without ticking every page. Starting
   * a book records today as its start unless it already has one, finishing it records today as its finish, and
   * leaving finished clears that. Moving back to not started clears both dates.
   */
  public void changeProgress(Progress progress, Clock clock) {
    if (this.progress == progress) {
      return;
    }
    moveTo(progress, LocalDate.now(clock));
    updatedAt = clock.instant();
  }

  /**
   * Marks a page done. The first page of a book that hasn't been started moves it to in progress, and the last
   * remaining page finishes it.
   */
  public void markPageDone(int page, int pageCount, Clock clock) {
    checkPage(page, pageCount);
    if (pagesDone.contains(page)) {
      return;
    }
    var pages = new TreeSet<>(pagesDone);
    pages.add(page);
    pagesDone = List.copyOf(pages);
    var today = LocalDate.now(clock);
    if (progress == NOT_STARTED) {
      moveTo(IN_PROGRESS, today);
    }
    if (getPagesDone(pageCount).size() == pageCount) {
      moveTo(FINISHED, today);
    }
    updatedAt = clock.instant();
  }

  /** Marks a page not done. Doing that on a finished book moves it back to in progress. */
  public void markPageNotDone(int page, int pageCount, Clock clock) {
    checkPage(page, pageCount);
    if (!pagesDone.contains(page)) {
      return;
    }
    pagesDone = pagesDone.stream().filter(done -> done != page).toList();
    if (progress == FINISHED) {
      moveTo(IN_PROGRESS, LocalDate.now(clock));
    }
    updatedAt = clock.instant();
  }

  /** Sets all three ratings, where null means not rated. A refused rating leaves every rating as it was. */
  public void rate(@Nullable Integer overall, @Nullable Integer design, @Nullable Integer color, Clock clock) {
    checkRating(overall);
    checkRating(design);
    checkRating(color);
    if (Objects.equals(rating, overall) && Objects.equals(designRating, design)
      && Objects.equals(colorRating, color)) {
      return;
    }
    rating = overall;
    designRating = design;
    colorRating = color;
    updatedAt = clock.instant();
  }

  /** Keeps the notes trimmed, and blank notes as none. */
  public void changeNotes(@Nullable String notes, Clock clock) {
    var stripped = notes == null || notes.isBlank() ? null : notes.strip();
    if (Objects.equals(this.notes, stripped)) {
      return;
    }
    this.notes = stripped;
    updatedAt = clock.instant();
  }

  private void moveTo(Progress next, LocalDate today) {
    if (progress == next) {
      return;
    }
    switch (next) {
      case NOT_STARTED -> {
        startedOn = null;
        finishedOn = null;
      }
      case IN_PROGRESS -> {
        if (startedOn == null) {
          startedOn = today;
        }
        finishedOn = null;
        owned = true;
      }
      case FINISHED -> {
        finishedOn = today;
        owned = true;
      }
    }
    progress = next;
  }

  private static void checkPage(int page, int pageCount) {
    if (page < 1 || page > pageCount) {
      throw new IllegalArgumentException(
        "There's no page " + page + " in this book. Its pages go from 1 to " + pageCount + ".");
    }
  }

  private static void checkRating(@Nullable Integer rating) {
    if (rating != null && (rating < 0 || rating > 5)) {
      throw new IllegalArgumentException("A rating must be between 0 and 5.");
    }
  }
}
