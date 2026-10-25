package dev.erichaag.dottracker.book;

import static dev.erichaag.dottracker.book.LineColor.MINI;
import static dev.erichaag.dottracker.book.LineColor.PREMIUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductLineTest {

  @Test
  void theNameIsTrimmed() {
    // When
    var line = new ProductLine("  Dot by Letter Mini ", 20, 2, MINI);

    // Then
    assertThat(line.getName()).isEqualTo("Dot by Letter Mini");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void aBlankNameIsRefused(String name) {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> new ProductLine(name, 20, 2, MINI))
      .withMessage("A product line needs a name.");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void theDefaultPageCountIsAtLeastOne(int defaultPageCount) {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> new ProductLine("Dot by Letter Mini", defaultPageCount, 2, MINI))
      .withMessage("A book needs at least one page.");
  }

  @Test
  void updatingALineChangesItsNamePageCountPlaceAndColor() {
    // Given
    var line = new ProductLine("Dot by Letter Mini", 20, 2, MINI);

    // When
    line.update(" Dot by Letter Large Dot ", 25, 3, PREMIUM);

    // Then
    assertThat(line.getName()).isEqualTo("Dot by Letter Large Dot");
    assertThat(line.getDefaultPageCount()).isEqualTo(25);
    assertThat(line.getSortOrder()).isEqualTo(3);
    assertThat(line.getColor()).isEqualTo(PREMIUM);
  }

  @Test
  void aRefusedUpdateLeavesTheLineAsItWas() {
    // Given
    var line = new ProductLine("Dot by Letter Mini", 20, 2, MINI);

    // When / Then
    assertThatIllegalArgumentException().isThrownBy(() -> line.update("Dot by Letter Large Dot", 0, 3, PREMIUM));
    assertThat(line.getName()).isEqualTo("Dot by Letter Mini");
    assertThat(line.getDefaultPageCount()).isEqualTo(20);
    assertThat(line.getSortOrder()).isEqualTo(2);
    assertThat(line.getColor()).isEqualTo(MINI);
  }
}
