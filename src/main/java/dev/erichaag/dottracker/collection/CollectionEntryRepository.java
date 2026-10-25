package dev.erichaag.dottracker.collection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionEntryRepository extends JpaRepository<CollectionEntry, UUID> {

  List<CollectionEntry> findByMemberId(UUID memberId);

  List<CollectionEntry> findByBookId(UUID bookId);

  Optional<CollectionEntry> findByMemberIdAndBookId(UUID memberId, UUID bookId);

  boolean existsByBookId(UUID bookId);
}
