package dev.erichaag.dottracker.book;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import dev.erichaag.dottracker.PostgresTestConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Checks the catalog that {@code V2__catalog.sql} seeds against {@code inventory/catalog.csv}, and that file against
 * the spreadsheet's tabs, so neither the curation nor the migration can drift. Gradle runs tests from the project
 * directory, where {@code inventory/} is.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(PostgresTestConfiguration.class)
class CatalogSeedTest {

  private static final Path INVENTORY = Path.of("inventory");

  // A listing in catalog.csv is the retailer, then its price such as $12.99 if it has one, then "exclusive" if it is
  private static final Pattern LISTING = Pattern.compile("(.+?)(?: \\$(\\d+)\\.(\\d{2}))?( exclusive)?");

  @Autowired
  JdbcClient jdbc;

  @Test
  void eachProductLineHasTheBooksCatalogCsvGivesIt() {
    // Given
    var expected = catalog().stream().collect(groupingBy(CatalogBook::productLine, counting()));

    // When
    var counts = jdbc.sql("""
        select product_line.name, count(book.id)
        from product_line left join book on book.product_line_id = product_line.id
        group by product_line.name
        """)
      .query((row, number) -> Map.entry(row.getString(1), row.getLong(2)))
      .list();

    // Then
    assertThat(counts.stream().collect(toMap(Map.Entry::getKey, Map.Entry::getValue))).isEqualTo(expected);
    assertThat(expected).hasSize(16);
    assertThat(expected.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(93);
  }

  @Test
  void theProductLinesComeInTheOrderCatalogCsvListsThemWithItsPageCounts() {
    // Given
    var expected = new LinkedHashMap<String, Integer>();
    catalog().forEach(book -> expected.putIfAbsent(book.productLine(), book.pageCount()));

    // When
    var lines = jdbc.sql("select name, default_page_count from product_line order by sort_order")
      .query((row, number) -> Map.entry(row.getString(1), row.getInt(2)))
      .list();

    // Then
    assertThat(lines).containsExactlyElementsOf(expected.entrySet());
    assertThat(catalog()).allSatisfy(book ->
      assertThat(book.pageCount()).isEqualTo(expected.get(book.productLine())));
  }

  @Test
  void everyRowOfTheSpreadsheetMatchesABookByNameOrOtherName() {
    // Given
    var names = new HashSet<String>();
    for (var book : seeded()) {
      names.add(book.productLine() + " / " + book.name());
      book.alsoKnownAs().forEach(alias -> names.add(book.productLine() + " / " + alias));
    }
    var rows = spreadsheetRows();

    // When
    var unmatched = rows.stream()
      .filter(row -> !names.contains(cell(row, "Type").replace(" - ", " ") + " / " + cell(row, "Name")))
      .toList();

    // Then
    assertThat(rows).hasSize(156);
    assertThat(unmatched).isEmpty();
  }

  @Test
  void theDatabaseHoldsCatalogCsvBookForBook() {
    // When
    var seeded = seeded();

    // Then
    assertThat(seeded).containsExactlyInAnyOrderElementsOf(catalog());
  }

  /** Every book in the database with its listings in order, in the same shape as a row of catalog.csv. */
  private List<CatalogBook> seeded() {
    var listings = new LinkedHashMap<UUID, List<CatalogListing>>();
    jdbc.sql("select book_id, retailer, price_cents, exclusive from book_listing order by book_id, position")
      .query(row -> {
        listings.computeIfAbsent(row.getObject("book_id", UUID.class), id -> new ArrayList<>())
          .add(new CatalogListing(row.getString("retailer"), row.getObject("price_cents", Integer.class),
            row.getBoolean("exclusive")));
      });
    return jdbc.sql("""
        select book.id, product_line.name as product_line, book.name, book.also_known_as, book.availability,
          book.page_count
        from book join product_line on product_line.id = book.product_line_id
        """)
      .query((row, number) -> new CatalogBook(
        row.getString("product_line"),
        row.getString("name"),
        List.of((String[]) row.getArray("also_known_as").getArray()),
        row.getString("availability"),
        row.getInt("page_count"),
        listings.getOrDefault(row.getObject("id", UUID.class), List.of())))
      .list();
  }

  private static List<CatalogBook> catalog() {
    return read(INVENTORY.resolve("catalog.csv")).stream()
      .map(row -> new CatalogBook(
        cell(row, "product_line"),
        cell(row, "name"),
        split(cell(row, "also_known_as")),
        cell(row, "availability"),
        Integer.parseInt(cell(row, "page_count")),
        split(cell(row, "listings")).stream().map(CatalogSeedTest::listing).toList()))
      .toList();
  }

  private static CatalogListing listing(String listing) {
    var matcher = LISTING.matcher(listing);
    assertThat(matcher.matches()).as("A listing in catalog.csv: %s", listing).isTrue();
    var dollars = matcher.group(2);
    var cents = matcher.group(3);
    var priceCents = dollars == null || cents == null
      ? null
      : Integer.parseInt(dollars) * 100 + Integer.parseInt(cents);
    return new CatalogListing(matcher.group(1), priceCents, matcher.group(4) != null);
  }

  /** Every row of every tab except Miscellaneous, which holds no books, leaving out blank rows. */
  private static List<Map<String, String>> spreadsheetRows() {
    try (Stream<Path> files = Files.list(INVENTORY)) {
      var tabs = files
        .filter(file -> file.getFileName().toString().startsWith("Lifelines Flow Art - "))
        .filter(file -> !file.getFileName().toString().contains("Miscellaneous"))
        .toList();
      assertThat(tabs).hasSize(5);
      return tabs.stream().flatMap(tab -> read(tab).stream()).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The rows of a CSV file as maps from header to trimmed cell, leaving out blank rows. The Winter tab's first header
   * reads "Column 1" and is read as "Type". No cell in these files is quoted, so a quote fails the read rather than
   * being misread.
   */
  private static List<Map<String, String>> read(Path file) {
    try {
      var lines = Files.readAllLines(file);
      var header = cells(lines.getFirst()).stream().map(name -> name.equals("Column 1") ? "Type" : name).toList();
      var rows = new ArrayList<Map<String, String>>();
      for (var line : lines.subList(1, lines.size())) {
        var cells = cells(line);
        if (cells.stream().allMatch(String::isBlank)) {
          continue;
        }
        var row = new LinkedHashMap<String, String>();
        for (var i = 0; i < header.size(); i++) {
          row.put(header.get(i), i < cells.size() ? cells.get(i).strip() : "");
        }
        rows.add(row);
      }
      return rows;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String cell(Map<String, String> row, String column) {
    return Objects.requireNonNull(row.get(column), column);
  }

  private static List<String> cells(String line) {
    assertThat(line).as("A line of a CSV file").doesNotContain("\"");
    return Arrays.asList(line.split(",", -1));
  }

  private static List<String> split(String cell) {
    return cell.isEmpty() ? List.of() : List.of(cell.split("; "));
  }

  record CatalogBook(String productLine, String name, List<String> alsoKnownAs, String availability, int pageCount,
    List<CatalogListing> listings) {
  }

  record CatalogListing(String retailer, @Nullable Integer priceCents, boolean exclusive) {
  }
}
