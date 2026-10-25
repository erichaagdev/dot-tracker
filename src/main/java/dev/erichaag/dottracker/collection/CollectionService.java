package dev.erichaag.dottracker.collection;

import dev.erichaag.dottracker.book.Book;
import dev.erichaag.dottracker.book.BookDeleting;
import dev.erichaag.dottracker.book.BookService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each member's own record of the books in the catalog. A member's entry for a book is created the first time they
 * change anything about it, and every change answers with the entry and the book's page count, which decides which of
 * its pages count.
 */
@Service
public class CollectionService {

  private final CollectionEntryRepository repository;

  private final BookService books;

  private final Clock clock;

  public CollectionService(CollectionEntryRepository repository, BookService books, Clock clock) {
    this.repository = repository;
    this.books = books;
    this.clock = clock;
  }

  /** Every entry of a member, each with its book's page count. */
  @Transactional(readOnly = true)
  public List<TrackedBook> findAll(UUID memberId) {
    Map<UUID, Integer> pageCounts = books.findAll().stream()
      .collect(Collectors.toMap(book -> Objects.requireNonNull(book.getId()), Book::getPageCount));
    return repository.findByMemberId(memberId).stream()
      .map(entry -> new TrackedBook(entry, pageCounts.getOrDefault(entry.getBookId(), 0)))
      .toList();
  }

  @Transactional
  public TrackedBook changeOwned(UUID memberId, UUID bookId, boolean owned) {
    var book = books.getById(bookId);
    var entry = entry(memberId, bookId);
    entry.changeOwned(owned, clock);
    return new TrackedBook(entry, book.getPageCount());
  }

  @Transactional
  public TrackedBook changeWanted(UUID memberId, UUID bookId, @Nullable Wanted wanted) {
    var book = books.getById(bookId);
    var entry = entry(memberId, bookId);
    entry.changeWanted(wanted, clock);
    return new TrackedBook(entry, book.getPageCount());
  }

  @Transactional
  public TrackedBook changeProgress(UUID memberId, UUID bookId, Progress progress) {
    var book = books.getById(bookId);
    var entry = entry(memberId, bookId);
    entry.changeProgress(progress, clock);
    return new TrackedBook(entry, book.getPageCount());
  }

  @Transactional
  public TrackedBook markPageDone(UUID memberId, UUID bookId, int page) {
    var book = books.getById(bookId);
    var entry = entry(memberId, bookId);
    entry.markPageDone(page, book.getPageCount(), clock);
    return new TrackedBook(entry, book.getPageCount());
  }

  @Transactional
  public TrackedBook markPageNotDone(UUID memberId, UUID bookId, int page) {
    var book = books.getById(bookId);
    var entry = entry(memberId, bookId);
    entry.markPageNotDone(page, book.getPageCount(), clock);
    return new TrackedBook(entry, book.getPageCount());
  }

  /** Sets all three ratings at once, so a null clears that rating. */
  @Transactional
  public TrackedBook rate(UUID memberId, UUID bookId, @Nullable Integer overall, @Nullable Integer design,
    @Nullable Integer color) {
    var book = books.getById(bookId);
    var entry = entry(memberId, bookId);
    entry.rate(overall, design, color, clock);
    return new TrackedBook(entry, book.getPageCount());
  }

  @Transactional
  public TrackedBook changeNotes(UUID memberId, UUID bookId, @Nullable String notes) {
    var book = books.getById(bookId);
    var entry = entry(memberId, bookId);
    entry.changeNotes(notes, clock);
    return new TrackedBook(entry, book.getPageCount());
  }

  /** Refuses to delete a book anyone has an entry for, since retiring it keeps everyone's record of it. */
  @EventListener
  public void refuseDeletingATrackedBook(BookDeleting event) {
    if (repository.existsByBookId(event.bookId())) {
      throw new IllegalArgumentException(
        "Someone in the group is already tracking this book, so it can't be deleted. Mark it as retired instead.");
    }
  }

  // A refused change rolls the transaction back, so a refused first change leaves no entry behind
  private CollectionEntry entry(UUID memberId, UUID bookId) {
    return repository.findByMemberIdAndBookId(memberId, bookId)
      .orElseGet(() -> repository.save(new CollectionEntry(memberId, bookId, clock)));
  }

  /** A member's entry for a book, with the book's page count, which decides which of the entry's pages count. */
  public record TrackedBook(CollectionEntry entry, int pageCount) {
  }
}
