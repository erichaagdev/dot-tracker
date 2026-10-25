package dev.erichaag.dottracker.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import dev.erichaag.dottracker.PostgresTestConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Checks the starting catalog that {@code V2__catalog.sql} seeds. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(PostgresTestConfiguration.class)
class CatalogSeedTest {

  @Autowired
  JdbcClient jdbc;

  @Test
  void theCatalogStartsWith93Books() {
    // When
    var count = jdbc.sql("select count(*) from book").query(Long.class).single();

    // Then
    assertThat(count).isEqualTo(93);
  }

  @Test
  void theSixteenProductLinesComeInSortOrder() {
    // When
    var lines = jdbc.sql("select name from product_line order by sort_order").query(String.class).list();

    // Then
    assertThat(lines).containsExactly(
      "Dot by Letter",
      "Dot by Letter Mini",
      "Dot by Letter Large Dot",
      "Dot by Letter Metallic",
      "Dot by Letter Premium",
      "Color to Reveal",
      "Color to Reveal Scented",
      "Dot Pop",
      "Illuminate & Create",
      "Pogo Dot",
      "Stamp by Shape",
      "Trace by Groove",
      "Trace by Letter",
      "Trace by Line",
      "Trace to Doodle",
      "Trace Within"
    );
  }

  @Test
  void largeDotBooksHave25PagesAndEveryOtherBookHas20() {
    // When
    var books = jdbc.sql("""
        select product_line.name, book.page_count
        from book join product_line on product_line.id = book.product_line_id
        """)
      .query((row, number) -> Map.entry(row.getString(1), row.getInt(2)))
      .list();

    // Then
    assertThat(books).hasSize(93);
    assertThat(books).filteredOn(book -> book.getKey().equals("Dot by Letter Large Dot"))
      .isNotEmpty()
      .allSatisfy(book -> assertThat(book.getValue()).isEqualTo(25));
    assertThat(books).filteredOn(book -> !book.getKey().equals("Dot by Letter Large Dot"))
      .allSatisfy(book -> assertThat(book.getValue()).isEqualTo(20));
  }

  @Test
  void theThreeTargetExclusivesAreMarkedExclusive() {
    // When
    var exclusives = jdbc.sql("""
        select book.name, book_listing.retailer
        from book join book_listing on book_listing.book_id = book.id
        where book_listing.exclusive
        """)
      .query((row, number) -> Map.entry(row.getString(1), row.getString(2)))
      .list();

    // Then
    assertThat(exclusives).containsExactlyInAnyOrder(
      Map.entry("Cabin Getaway", "Target"),
      Map.entry("Corner Cafe", "Target"),
      Map.entry("Cozy Companions", "Target")
    );
  }
}
