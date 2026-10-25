package dev.erichaag.dottracker.book;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

import dev.erichaag.dottracker.book.BookService.BookDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookRestController {

  private final BookService service;

  public BookRestController(BookService service) {
    this.service = service;
  }

  @GetMapping
  public List<BookResponse> books() {
    return service.findAll().stream().map(BookResponse::from).toList();
  }

  @GetMapping("/{id}")
  public BookResponse book(@PathVariable UUID id) {
    return BookResponse.from(service.getById(id));
  }

  @PostMapping
  @ResponseStatus(CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public BookResponse add(@Valid @RequestBody BookRequest request) {
    return BookResponse.from(service.add(request.details()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public BookResponse update(@PathVariable UUID id, @Valid @RequestBody BookRequest request) {
    return BookResponse.from(service.update(id, request.details()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  /** A book as an admin enters it. Without a page count, it gets its product line's default. */
  public record BookRequest(
    @NotNull UUID productLineId,
    @NotNull String name,
    @NotNull Availability availability,
    @Nullable Integer pageCount,
    @NotNull @Valid List<@NotNull ListingRequest> listings
  ) {

    BookDetails details() {
      return new BookDetails(productLineId, name, availability, pageCount,
        listings.stream().map(ListingRequest::listing).toList());
    }
  }

  public record ListingRequest(@NotNull String retailer, @Nullable Integer priceCents, @Nullable String url,
    boolean exclusive) {

    Listing listing() {
      return new Listing(retailer, priceCents, url, exclusive);
    }
  }

  /** A book, which is a store exclusive when any of its listings is exclusive. */
  public record BookResponse(UUID id, UUID productLineId, String name, Availability availability, int pageCount,
    List<ListingResponse> listings, boolean exclusive) {

    static BookResponse from(Book book) {
      return new BookResponse(Objects.requireNonNull(book.getId()), book.getProductLineId(), book.getName(),
        book.getAvailability(), book.getPageCount(), book.getListings().stream().map(ListingResponse::from).toList(),
        book.isExclusive());
    }
  }

  public record ListingResponse(String retailer, @Nullable Integer priceCents, @Nullable String url,
    boolean exclusive) {

    static ListingResponse from(Listing listing) {
      return new ListingResponse(listing.retailer(), listing.priceCents(), listing.url(), listing.exclusive());
    }
  }
}
