package dev.erichaag.dottracker.book;

import jakarta.persistence.Embeddable;
import java.net.URI;
import java.net.URISyntaxException;
import org.jspecify.annotations.Nullable;

/**
 * A store that sells a book, with its price in cents when it's known. An exclusive listing is a store that has the
 * book to itself, which makes the book a store exclusive. The retailer is trimmed and never blank, a price is never
 * negative, a blank link is no link, and any other link is a web address with an http or https scheme and a host, so
 * it can only ever open a web page.
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
    if (url != null && !isWebLink(url)) {
      throw new IllegalArgumentException("A store link has to start with http:// or https://.");
    }
  }

  private static boolean isWebLink(String url) {
    try {
      var uri = new URI(url);
      var scheme = uri.getScheme();
      return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null;
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
