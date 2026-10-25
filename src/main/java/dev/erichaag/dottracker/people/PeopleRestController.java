package dev.erichaag.dottracker.people;

import dev.erichaag.dottracker.collection.CollectionRestController.CollectionEntryResponse;
import dev.erichaag.dottracker.people.PeopleService.BookEntries;
import dev.erichaag.dottracker.people.PeopleService.MemberEntry;
import dev.erichaag.dottracker.people.PeopleService.Person;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What everyone in the group has, open to every member. These routes carry each member's display name but never their
 * email or role, which only admins see through {@code /api/members}.
 */
@RestController
@RequestMapping("/api")
public class PeopleRestController {

  private final PeopleService service;

  public PeopleRestController(PeopleService service) {
    this.service = service;
  }

  @GetMapping("/people")
  public List<PersonResponse> people() {
    return service.findAll().stream().map(PersonResponse::from).toList();
  }

  @GetMapping("/people/{id}/collection")
  public List<CollectionEntryResponse> collection(@PathVariable UUID id) {
    return service.findCollection(id).stream().map(CollectionEntryResponse::from).toList();
  }

  @GetMapping("/books/{id}/entries")
  public BookEntriesResponse entries(@PathVariable UUID id) {
    return BookEntriesResponse.from(service.findEntries(id));
  }

  public record PersonResponse(UUID id, String displayName, long owned, long inProgress, long finished) {

    static PersonResponse from(Person person) {
      var counts = person.counts();
      return new PersonResponse(Objects.requireNonNull(person.member().getId()), person.member().getDisplayName(),
        counts.owned(), counts.inProgress(), counts.finished());
    }
  }

  /** Everyone's entries for a book by name, and the average overall rating, which is null when nobody rated it. */
  public record BookEntriesResponse(@Nullable Double averageRating, long ratingCount,
    List<MemberEntryResponse> entries) {

    static BookEntriesResponse from(BookEntries entries) {
      return new BookEntriesResponse(entries.averageRating(), entries.ratingCount(),
        entries.entries().stream().map(MemberEntryResponse::from).toList());
    }
  }

  public record MemberEntryResponse(UUID memberId, String displayName, CollectionEntryResponse entry) {

    static MemberEntryResponse from(MemberEntry entry) {
      return new MemberEntryResponse(Objects.requireNonNull(entry.member().getId()),
        entry.member().getDisplayName(), CollectionEntryResponse.from(entry.tracked()));
    }
  }
}
