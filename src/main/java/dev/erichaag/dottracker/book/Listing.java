package dev.erichaag.dottracker.book;

import jakarta.persistence.Embeddable;
import org.jspecify.annotations.Nullable;

/**
 * A store that sells a book, with its price in cents when it's known. An exclusive listing is a store that has the
 * book to itself, which makes the book a store exclusive. The retailer is trimmed and never blank, a price is never
 * negative, and a blank link is no link.
 */
@Embeddable
public record Listing(String retailer, @Nullable Integer priceCents, @Nullable String url, boolean exclusive) {

  public Listing {
    retailer = retailer.strip();
    if (retailer.isEmpty()) {
      throw new IllegalArgumentException("Every store needs a name.");
    }
    if (priceCents != null && priceCents < 0) {
      throw new IllegalArgumentException("A price can't be less than zero.");
    }
    url = url == null || url.isBlank() ? null : url.strip();
  }
}
