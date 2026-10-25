package dev.erichaag.dottracker.book;

import static jakarta.persistence.FetchType.EAGER;
import static org.hibernate.annotations.UuidGenerator.Style.VERSION_7;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A book in the shared catalog. Its name is trimmed and never blank, and it has at least one page. It's a store
 * exclusive when any of its listings is exclusive.
 */
@Entity
public class Book {

  @Id
  @UuidGenerator(style = VERSION_7)
  private @Nullable UUID id;

  private UUID productLineId = new UUID(0, 0);

  private String name = "";

  @Enumerated(EnumType.STRING)
  private Availability availability = Availability.UNKNOWN;

  private int pageCount;

  @ElementCollection(fetch = EAGER)
  @CollectionTable(name = "book_listing", joinColumns = @JoinColumn(name = "book_id"))
  @OrderColumn(name = "position")
  private List<Listing> listings = new ArrayList<>();

  private Instant createdAt = Instant.EPOCH;

  private Instant updatedAt = Instant.EPOCH;

  @Version
  private @Nullable Long version;

  protected Book() {
  }

  public Book(UUID productLineId, String name, Availability availability, int pageCount, List<Listing> listings,
    Instant now) {
    apply(productLineId, name, availability, pageCount, listings);
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** The form a name is stored and compared in. */
  public static String normalizeName(String name) {
    return name.strip();
  }

  public @Nullable UUID getId() {
    return id;
  }

  public UUID getProductLineId() {
    return productLineId;
  }

  public String getName() {
    return name;
  }

  public Availability getAvailability() {
    return availability;
  }

  public int getPageCount() {
    return pageCount;
  }

  public List<Listing> getListings() {
    return List.copyOf(listings);
  }

  public boolean isExclusive() {
    return listings.stream().anyMatch(Listing::exclusive);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void update(UUID productLineId, String name, Availability availability, int pageCount,
    List<Listing> listings, Instant now) {
    apply(productLineId, name, availability, pageCount, listings);
    this.updatedAt = now;
  }

  private void apply(UUID productLineId, String name, Availability availability, int pageCount,
    List<Listing> listings) {
    var normalized = normalizeName(name);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("A book needs a name.");
    }
    if (pageCount < 1) {
      throw new IllegalArgumentException("A book needs at least one page.");
    }
    this.productLineId = productLineId;
    this.name = normalized;
    this.availability = availability;
    this.pageCount = pageCount;
    this.listings.clear();
    this.listings.addAll(listings);
  }
}
