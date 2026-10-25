package dev.erichaag.dottracker.book;

import static org.springframework.http.HttpStatus.CREATED;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-lines")
public class ProductLineRestController {

  private final BookService service;

  public ProductLineRestController(BookService service) {
    this.service = service;
  }

  @GetMapping
  public List<ProductLineResponse> productLines() {
    return service.findProductLines().stream().map(ProductLineResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public ProductLineResponse add(@Valid @RequestBody ProductLineRequest request) {
    return ProductLineResponse.from(
      service.addProductLine(request.name(), request.defaultPageCount(), request.sortOrder()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ProductLineResponse update(@PathVariable UUID id, @Valid @RequestBody ProductLineRequest request) {
    return ProductLineResponse.from(
      service.updateProductLine(id, request.name(), request.defaultPageCount(), request.sortOrder()));
  }

  public record ProductLineRequest(@NotNull String name, @NotNull Integer defaultPageCount,
    @NotNull Integer sortOrder) {
  }

  public record ProductLineResponse(UUID id, String name, int defaultPageCount, int sortOrder) {

    static ProductLineResponse from(ProductLine line) {
      return new ProductLineResponse(Objects.requireNonNull(line.getId()), line.getName(),
        line.getDefaultPageCount(), line.getSortOrder());
    }
  }
}
