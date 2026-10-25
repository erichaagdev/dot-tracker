package dev.erichaag.dottracker.book;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shared catalog of books and product lines. A line's books have names of their own, ignoring case, and so do the
 * lines, which this checks before the database's unique indexes would.
 */
@Service
public class BookService {

  private static final Logger logger = LoggerFactory.getLogger(BookService.class);

  private final BookRepository books;

  private final ProductLineRepository productLines;

  private final ApplicationEventPublisher events;

  private final Clock clock;

  public BookService(BookRepository books, ProductLineRepository productLines, ApplicationEventPublisher events,
    Clock clock) {
    this.books = books;
    this.productLines = productLines;
    this.events = events;
    this.clock = clock;
  }

  /** Every book with its listings, by name. */
  @Transactional(readOnly = true)
  public List<Book> findAll() {
    return books.findAllByOrderByNameAsc();
  }

  @Transactional(readOnly = true)
  public Book getById(UUID id) {
    return books.findById(id).orElseThrow(() -> new NoSuchElementException("That book isn't in the catalog anymore."));
  }

  /** Adds a book, which gets its product line's default page count unless it has its own. */
  @Transactional
  public Book add(BookDetails details) {
    var line = getProductLine(details.productLineId());
    var name = Book.normalizeName(details.name());
    books.findByProductLineIdAndNameIgnoreCase(id(line), name).ifPresent(existing -> {
      throw duplicateBook(line, existing);
    });
    var book = books.save(new Book(id(line), name, details.availability(), pageCount(details, line),
      details.listings(), clock.instant()));
    logger.info("Added {} to {}", book.getName(), line.getName());
    return book;
  }

  /** Changes a book, which gets its product line's default page count unless it has its own. */
  @Transactional
  public Book update(UUID id, BookDetails details) {
    var book = getById(id);
    var line = getProductLine(details.productLineId());
    var name = Book.normalizeName(details.name());
    // Checked before the book changes, since the query would flush a changed book into the unique index first
    books.findByProductLineIdAndNameIgnoreCaseAndIdNot(id(line), name, id).ifPresent(existing -> {
      throw duplicateBook(line, existing);
    });
    book.update(id(line), name, details.availability(), pageCount(details, line), details.listings(),
      clock.instant());
    logger.info("Changed {} in {}", book.getName(), line.getName());
    return book;
  }

  /**
   * Removes a book unless a listener of {@link BookDeleting} refuses, as the collection domain does for a book anyone
   * tracks. One that's already gone is left gone, so a retried request does no harm.
   */
  @Transactional
  public void delete(UUID id) {
    events.publishEvent(new BookDeleting(id));
    books.deleteById(id);
    logger.info("Deleted the book {}", id);
  }

  /** Every product line in sort order, then by name. */
  @Transactional(readOnly = true)
  public List<ProductLine> findProductLines() {
    return productLines.findAllByOrderBySortOrderAscNameAsc();
  }

  @Transactional
  public ProductLine addProductLine(String name, int defaultPageCount, int sortOrder, LineColor color) {
    var stripped = name.strip();
    productLines.findByNameIgnoreCase(stripped).ifPresent(existing -> {
      throw duplicateProductLine(existing);
    });
    var line = productLines.save(new ProductLine(stripped, defaultPageCount, sortOrder, color));
    logger.info("Added the product line {}", line.getName());
    return line;
  }

  @Transactional
  public ProductLine updateProductLine(UUID id, String name, int defaultPageCount, int sortOrder,
    LineColor color) {
    var line = getProductLine(id);
    var stripped = name.strip();
    productLines.findByNameIgnoreCaseAndIdNot(stripped, id).ifPresent(existing -> {
      throw duplicateProductLine(existing);
    });
    line.update(stripped, defaultPageCount, sortOrder, color);
    logger.info("Changed the product line {}", line.getName());
    return line;
  }

  private ProductLine getProductLine(UUID id) {
    return productLines.findById(id)
      .orElseThrow(() -> new NoSuchElementException("That product line isn't in the catalog anymore."));
  }

  private static int pageCount(BookDetails details, ProductLine line) {
    var pageCount = details.pageCount();
    return pageCount != null ? pageCount : line.getDefaultPageCount();
  }

  private static IllegalArgumentException duplicateBook(ProductLine line, Book existing) {
    return new IllegalArgumentException(line.getName() + " already has a book called " + existing.getName() + ".");
  }

  private static IllegalArgumentException duplicateProductLine(ProductLine existing) {
    return new IllegalArgumentException("There's already a product line called " + existing.getName() + ".");
  }

  private static UUID id(ProductLine line) {
    return Objects.requireNonNull(line.getId());
  }

  /** What an admin gives a book. Without a page count, the book gets its product line's default. */
  public record BookDetails(UUID productLineId, String name, Availability availability, @Nullable Integer pageCount,
    List<Listing> listings) {
  }
}
