package dev.erichaag.dottracker.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListingTest {

  @Test
  void theRetailerAndLinkAreTrimmed() {
    // When
    var listing = new Listing("  Turner Toys ", 1499, " https://turnertoys.com/ ", false);

    // Then
    assertThat(listing.retailer()).isEqualTo("Turner Toys");
    assertThat(listing.url()).isEqualTo("https://turnertoys.com/");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void aBlankRetailerIsRefused(String retailer) {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> new Listing(retailer, 1499, null, false))
      .withMessage("Every store needs a name.");
  }

  @Test
  void aNegativePriceIsRefused() {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> new Listing("Turner Toys", -1, null, false))
      .withMessage("A price can't be less than zero.");
  }

  @Test
  void aListingCanBeFreeOrHaveNoKnownPrice() {
    // When
    var free = new Listing("Lifelines", 0, null, false);
    var unknown = new Listing("Target", null, null, true);

    // Then
    assertThat(free.priceCents()).isZero();
    assertThat(unknown.priceCents()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void aBlankLinkIsNoLink(String url) {
    // When
    var listing = new Listing("Turner Toys", 1499, url, false);

    // Then
    assertThat(listing.url()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://turnertoys.com/", "https://www.target.com/p/cabin-getaway", "HTTPS://TARGET.COM"})
  void aWebLinkIsKept(String url) {
    // When
    var listing = new Listing("Turner Toys", 1499, url, false);

    // Then
    assertThat(listing.url()).isEqualTo(url);
  }

  @ParameterizedTest
  @ValueSource(strings = {"javascript:alert(1)", "ftp://turnertoys.com/", "turnertoys", "turnertoys.com/cabin",
    "https://", "https:turnertoys.com", "https://turner toys.com/"})
  void aLinkThatIsNotAWebAddressIsRefused(String url) {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> new Listing("Turner Toys", 1499, url, false))
      .withMessage("A store link has to start with http:// or https://.");
  }
}
