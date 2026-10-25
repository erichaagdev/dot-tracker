package dev.erichaag.dottracker.book;

import static dev.erichaag.dottracker.FixedClockConfiguration.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import dev.erichaag.dottracker.FixedClockConfiguration;
import dev.erichaag.dottracker.PostgresTestConfiguration;
import dev.erichaag.dottracker.book.BookService.BookDetails;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

// The real migrations run, so every test starts from the seeded catalog of 93 books in 16 product lines
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = NONE)
@Import({PostgresTestConfiguration.class, FixedClockConfiguration.class, BookService.class})
class BookServiceTest {

  @Autowired
  BookService service;

  @Autowired
  BookRepository books;

  @Autowired
  ProductLineRepository productLines;

  @Autowired
  TestEntityManager entityManager;

  @Autowired
  EntityManagerFactory entityManagerFactory;

  @Autowired
  JdbcClient jdbc;

  @Test
  void theWholeCatalogComesBackWithItsListingsInOneQuery() {
    // Given
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();

    // When
    var catalog = service.findAll();

    // Then
    assertThat(catalog).hasSize(93);
    assertThat(catalog).filteredOn(book -> book.getName().equals("Cabin Getaway")).singleElement()
      .extracting(Book::getListings)
      .isEqualTo(List.of(new Listing("Turner Toys", 1499, null, false), new Listing("Target", null, null, true)));
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  void theCatalogIsListedByName() {
    // When
    var catalog = service.findAll();

    // Then
    assertThat(catalog).extracting(Book::getName).startsWith("Americana", "Animals", "Animals", "Beautiful Horizons");
  }

  @Test
  void aBookIsFoundById() {
    // Given
    var lovestruck = seeded("Dot by Letter", "Lovestruck");

    // When
    var found = service.getById(id(lovestruck));

    // Then
    assertThat(found.getName()).isEqualTo("Lovestruck");
    assertThat(found.getAlsoKnownAs()).containsExactly("Made with Love", "Lovestruck/Made with Love");
    assertThat(found.getAvailability()).isEqualTo(Availability.RETIRED);
    assertThat(found.getListings()).containsExactly(new Listing("Lifelines", 1299, null, false));
  }

  @Test
  void aBookThatIsGoneIsReported() {
    // When / Then
    assertThatExceptionOfType(NoSuchElementException.class)
      .isThrownBy(() -> service.getById(UUID.randomUUID()))
      .withMessage("That book isn't in the catalog anymore.");
  }

  @Test
  void addingABookRecordsWhenItWasAddedAndChanged() {
    // When
    var book = service.add(details(line("Dot by Letter"), "Harvest Moon", null));

    // Then
    assertThat(book.getId()).isNotNull();
    assertThat(book.getCreatedAt()).isEqualTo(NOW);
    assertThat(book.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  void aNewBookGetsItsProductLinesDefaultPageCount() {
    // When
    var book = service.add(details(line("Dot by Letter Large Dot"), "Harvest Moon", null));

    // Then
    assertThat(book.getPageCount()).isEqualTo(25);
  }

  @Test
  void aNewBookCanHaveItsOwnPageCount() {
    // When
    var book = service.add(details(line("Dot by Letter Large Dot"), "Harvest Moon", 30));

    // Then
    assertThat(book.getPageCount()).isEqualTo(30);
  }

  @Test
  void otherNamesAndListingsAreStoredAndReadBackInOrder() {
    // Given
    var listings = List.of(
      new Listing("Turner Toys", 1499, "https://turnertoys.com/", false),
      new Listing("Target", null, null, true),
      new Listing("Lifelines", 1299, null, false)
    );
    var details = new BookDetails(id(line("Dot by Letter")), "Harvest Moon", List.of("Harvest Night", "Moon Harvest"),
      Availability.COMING_SOON, null, listings);
    var id = id(service.add(details));
    entityManager.flush();
    entityManager.clear();

    // When
    var book = service.getById(id);

    // Then
    assertThat(book.getAlsoKnownAs()).containsExactly("Harvest Night", "Moon Harvest");
    assertThat(book.getListings()).isEqualTo(listings);
    assertThat(book.isExclusive()).isTrue();
    assertThat(jdbc.sql("select also_known_as::text from book where id = ?").param(id).query(String.class).single())
      .isEqualTo("{\"Harvest Night\",\"Moon Harvest\"}");
  }

  @Test
  void aBookAlreadyInTheLineIsRefusedUnderItsStoredName() {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> service.add(details(line("Dot by Letter Large Dot"), "  mandalas ", null)))
      .withMessage("Dot by Letter Large Dot already has a book called Mandalas.");
  }

  @Test
  void anotherLineCanHaveABookOfTheSameName() {
    // When
    var book = service.add(details(line("Dot by Letter Mini"), "Americana", null));

    // Then
    assertThat(book.getName()).isEqualTo("Americana");
  }

  @Test
  void addingABookToAProductLineThatIsGoneIsRefused() {
    // When / Then
    assertThatExceptionOfType(NoSuchElementException.class)
      .isThrownBy(() -> service.add(details(UUID.randomUUID(), "Harvest Moon", null)))
      .withMessage("That product line isn't in the catalog anymore.");
  }

  @Test
  void updatingABookChangesItAndWhenItWasLastChanged() {
    // Given
    var winterGlow = seeded("Dot by Letter", "Winter's Glow");
    var addedAt = winterGlow.getCreatedAt();
    var details = new BookDetails(id(line("Dot by Letter")), "Winter's Glow", List.of("Winters Glow"),
      Availability.AVAILABLE, 24, List.of(new Listing("Turner Toys", 1599, null, false)));

    // When
    var book = service.update(id(winterGlow), details);

    // Then
    assertThat(book.getAlsoKnownAs()).containsExactly("Winters Glow");
    assertThat(book.getAvailability()).isEqualTo(Availability.AVAILABLE);
    assertThat(book.getPageCount()).isEqualTo(24);
    assertThat(book.getListings()).containsExactly(new Listing("Turner Toys", 1599, null, false));
    assertThat(book.getCreatedAt()).isEqualTo(addedAt).isNotEqualTo(NOW);
    assertThat(book.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  void aBookCanBeRenamedToADifferentCaseOfItsOwnName() {
    // Given
    var americana = seeded("Dot by Letter", "Americana");

    // When
    var book = service.update(id(americana), details(line("Dot by Letter"), "AMERICANA", null));

    // Then
    assertThat(book.getName()).isEqualTo("AMERICANA");
  }

  @Test
  void aBookCannotBeRenamedToAnotherBookInItsLine() {
    // Given
    var americana = seeded("Dot by Letter", "Americana");

    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> service.update(id(americana), details(line("Dot by Letter"), "botanicals", null)))
      .withMessage("Dot by Letter already has a book called Botanicals.");
    assertThat(americana.getName()).isEqualTo("Americana");
  }

  @Test
  void aBookCannotMoveToALineThatAlreadyHasItsName() {
    // Given
    var animals = seeded("Color to Reveal", "Animals");

    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> service.update(id(animals), details(line("Dot by Letter Mini"), "ANIMALS", null)))
      .withMessage("Dot by Letter Mini already has a book called Animals.");
  }

  @Test
  void deletingABookRemovesItAndItsListings() {
    // Given
    var cabinGetaway = id(seeded("Dot by Letter", "Cabin Getaway"));

    // When
    service.delete(cabinGetaway);
    entityManager.flush();

    // Then
    assertThat(books.findById(cabinGetaway)).isEmpty();
    assertThat(jdbc.sql("select count(*) from book_listing where book_id = ?").param(cabinGetaway)
      .query(Integer.class).single()).isZero();
  }

  @Test
  void deletingABookThatIsAlreadyGoneDoesNothing() {
    // When
    service.delete(UUID.randomUUID());

    // Then
    assertThat(books.count()).isEqualTo(93);
  }

  @Test
  void productLinesComeInSortOrderThenByName() {
    // Given
    service.addProductLine("Paint by Sticker", 20, 5);

    // When
    var lines = service.findProductLines();

    // Then
    assertThat(lines).extracting(ProductLine::getName).startsWith(
      "Dot by Letter",
      "Dot by Letter Mini",
      "Dot by Letter Large Dot",
      "Dot by Letter Metallic",
      "Dot by Letter Premium",
      "Paint by Sticker",
      "Color to Reveal"
    );
  }

  @Test
  void addingAProductLineKeepsItsNamePageCountAndPlace() {
    // When
    var line = service.addProductLine("  Paint by Sticker ", 12, 17);

    // Then
    assertThat(line.getId()).isNotNull();
    assertThat(line.getName()).isEqualTo("Paint by Sticker");
    assertThat(line.getDefaultPageCount()).isEqualTo(12);
    assertThat(line.getSortOrder()).isEqualTo(17);
  }

  @Test
  void aProductLineNameCannotBeUsedTwiceAndTheRefusalNamesTheStoredLine() {
    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> service.addProductLine(" dot pop ", 20, 17))
      .withMessage("There's already a product line called Dot Pop.");
  }

  @Test
  void changingALinesDefaultPageCountLeavesItsBooksAlone() {
    // Given
    var largeDot = line("Dot by Letter Large Dot");

    // When
    var line = service.updateProductLine(id(largeDot), "Dot by Letter Large Dot", 30, 3);

    // Then
    assertThat(line.getDefaultPageCount()).isEqualTo(30);
    assertThat(seeded("Dot by Letter Large Dot", "Mandalas").getPageCount()).isEqualTo(25);
  }

  @Test
  void aProductLineCannotBeRenamedToAnotherLine() {
    // Given
    var dotPop = line("Dot Pop");

    // When / Then
    assertThatIllegalArgumentException()
      .isThrownBy(() -> service.updateProductLine(id(dotPop), "pogo dot", 20, 8))
      .withMessage("There's already a product line called Pogo Dot.");
  }

  @Test
  void aProductLineCanBeRenamedToADifferentCaseOfItsOwnName() {
    // Given
    var dotPop = line("Dot Pop");

    // When
    var line = service.updateProductLine(id(dotPop), "DOT POP", 20, 8);

    // Then
    assertThat(line.getName()).isEqualTo("DOT POP");
  }

  @Test
  void changingAProductLineThatIsGoneIsRefused() {
    // When / Then
    assertThatExceptionOfType(NoSuchElementException.class)
      .isThrownBy(() -> service.updateProductLine(UUID.randomUUID(), "Paint by Sticker", 20, 17))
      .withMessage("That product line isn't in the catalog anymore.");
  }

  private ProductLine line(String name) {
    return productLines.findAll().stream().filter(line -> line.getName().equals(name)).findFirst().orElseThrow();
  }

  private Book seeded(String productLine, String name) {
    var lineId = id(line(productLine));
    return books.findAllByOrderByNameAsc().stream()
      .filter(book -> book.getProductLineId().equals(lineId) && book.getName().equals(name))
      .findFirst()
      .orElseThrow();
  }

  private static BookDetails details(ProductLine line, String name, @Nullable Integer pageCount) {
    return details(id(line), name, pageCount);
  }

  private static BookDetails details(UUID productLineId, String name, @Nullable Integer pageCount) {
    return new BookDetails(productLineId, name, List.of(), Availability.AVAILABLE, pageCount, List.of());
  }

  private static UUID id(ProductLine line) {
    return Objects.requireNonNull(line.getId());
  }

  private static UUID id(Book book) {
    return Objects.requireNonNull(book.getId());
  }
}
