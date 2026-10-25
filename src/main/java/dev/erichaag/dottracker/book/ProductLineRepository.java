package dev.erichaag.dottracker.book;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductLineRepository extends JpaRepository<ProductLine, UUID> {

  List<ProductLine> findAllByOrderBySortOrderAscNameAsc();

  Optional<ProductLine> findByNameIgnoreCase(String name);

  Optional<ProductLine> findByNameIgnoreCaseAndIdNot(String name, UUID id);
}
