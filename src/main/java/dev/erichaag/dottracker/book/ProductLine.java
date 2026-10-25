package dev.erichaag.dottracker.book;

import static org.hibernate.annotations.UuidGenerator.Style.VERSION_7;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A series of books, such as Dot by Letter Mini. Its name is trimmed and never blank, and a new book in the line gets
 * its default page count unless it's given its own. Lines are listed by sort order, then by name. Its color names the
 * family its covers are drawn in, so renaming a line leaves its color alone.
 */
@Entity
public class ProductLine {

  @Id
  @UuidGenerator(style = VERSION_7)
  private @Nullable UUID id;

  private String name = "";

  private int defaultPageCount;

  private int sortOrder;

  @Enumerated(EnumType.STRING)
  private LineColor color = LineColor.DBL;

  @Version
  private @Nullable Long version;

  protected ProductLine() {
  }

  public ProductLine(String name, int defaultPageCount, int sortOrder, LineColor color) {
    apply(name, defaultPageCount, sortOrder, color);
  }

  public @Nullable UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getDefaultPageCount() {
    return defaultPageCount;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public LineColor getColor() {
    return color;
  }

  /** Changes the line. Its books keep their own page counts when the default changes. */
  public void update(String name, int defaultPageCount, int sortOrder, LineColor color) {
    apply(name, defaultPageCount, sortOrder, color);
  }

  private void apply(String name, int defaultPageCount, int sortOrder, LineColor color) {
    var stripped = name.strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("A product line needs a name.");
    }
    if (defaultPageCount < 1) {
      throw new IllegalArgumentException("A book needs at least one page.");
    }
    this.name = stripped;
    this.defaultPageCount = defaultPageCount;
    this.sortOrder = sortOrder;
    this.color = color;
  }
}
