package dev.erichaag.dottracker.people;

import dev.erichaag.dottracker.collection.CollectionService;
import dev.erichaag.dottracker.collection.CollectionService.Counts;
import dev.erichaag.dottracker.collection.CollectionService.TrackedBook;
import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.MemberService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What everyone in the group has, for any member to see. There's nothing hidden, so every member's collection, with
 * its progress, ratings and notes, is open to the rest of the group. Each read loads in a fixed number of queries,
 * however many people and books there are.
 */
@Service
public class PeopleService {

  private final MemberService members;

  private final CollectionService collections;

  public PeopleService(MemberService members, CollectionService collections) {
    this.members = members;
    this.collections = collections;
  }

  /** Everyone in the group by name, each with how many books they own, are working on and have finished. */
  @Transactional(readOnly = true)
  public List<Person> findAll() {
    var counts = collections.countByMember();
    return members.findAll().stream()
      .map(member -> new Person(member, counts.getOrDefault(id(member), Counts.NONE)))
      .toList();
  }

  /** Every entry of one member, each with its book's page count. */
  @Transactional(readOnly = true)
  public List<TrackedBook> findCollection(UUID memberId) {
    members.getById(memberId);
    return collections.findAll(memberId);
  }

  /** Everyone's entry for one book, by name, with the group's average overall rating. */
  @Transactional(readOnly = true)
  public BookEntries findEntries(UUID bookId) {
    Map<UUID, TrackedBook> byMember = collections.findByBook(bookId).stream()
      .collect(Collectors.toMap(tracked -> tracked.entry().getMemberId(), Function.identity()));
    var entries = members.findAll().stream()
      .flatMap(member -> Optional.ofNullable(byMember.get(id(member)))
        .map(tracked -> new MemberEntry(member, tracked))
        .stream())
      .toList();
    var ratings = entries.stream()
      .map(entry -> entry.tracked().entry().getRating())
      .filter(Objects::nonNull)
      .mapToInt(Integer::intValue)
      .summaryStatistics();
    return new BookEntries(entries, ratings.getCount() == 0 ? null : ratings.getAverage(), ratings.getCount());
  }

  private static UUID id(Member member) {
    return Objects.requireNonNull(member.getId());
  }

  /** Someone in the group with how many books they own, are working on and have finished. */
  public record Person(Member member, Counts counts) {
  }

  /** One member's entry for a book. */
  public record MemberEntry(Member member, TrackedBook tracked) {
  }

  /**
   * Everyone's entries for a book and the average of their overall ratings, which is null when nobody has rated it.
   */
  public record BookEntries(List<MemberEntry> entries, @Nullable Double averageRating, long ratingCount) {
  }
}
