package dev.erichaag.dottracker.book;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, UUID> {

  // Fetches the listings in the same query, since an eager collection would otherwise take one query per book
  @EntityGraph(attributePaths = "listings")
  List<Book> findAllByOrderByNameAsc();

  Optional<Book> findByProductLineIdAndNameIgnoreCase(UUID productLineId, String name);

  Optional<Book> findByProductLineIdAndNameIgnoreCaseAndIdNot(UUID productLineId, String name, UUID id);
}
