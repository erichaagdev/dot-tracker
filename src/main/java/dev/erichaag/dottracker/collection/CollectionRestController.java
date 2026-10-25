package dev.erichaag.dottracker.collection;

import dev.erichaag.dottracker.collection.CollectionService.TrackedBook;
import dev.erichaag.dottracker.security.CurrentMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in member's own collection. Every write answers with the updated entry and can be retried safely. */
@RestController
@RequestMapping("/api/collection")
public class CollectionRestController {

  private final CollectionService service;

  private final CurrentMember currentMember;

  public CollectionRestController(CollectionService service, CurrentMember currentMember) {
    this.service = service;
    this.currentMember = currentMember;
  }

  @GetMapping
  public List<CollectionEntryResponse> collection() {
    return service.findAll(currentMember.id()).stream().map(CollectionEntryResponse::from).toList();
  }

  @PutMapping("/{bookId}/owned")
  public CollectionEntryResponse changeOwned(@PathVariable UUID bookId, @Valid @RequestBody OwnedRequest request) {
    return CollectionEntryResponse.from(service.changeOwned(currentMember.id(), bookId, request.owned()));
  }

  @PutMapping("/{bookId}/wanted")
  public CollectionEntryResponse changeWanted(@PathVariable UUID bookId, @RequestBody WantedRequest request) {
    return CollectionEntryResponse.from(service.changeWanted(currentMember.id(), bookId, request.wanted()));
  }

  @PutMapping("/{bookId}/progress")
  public CollectionEntryResponse changeProgress(@PathVariable UUID bookId,
    @Valid @RequestBody ProgressRequest request) {
    return CollectionEntryResponse.from(service.changeProgress(currentMember.id(), bookId, request.progress()));
  }

  @PutMapping("/{bookId}/pages/{page}")
  public CollectionEntryResponse markPageDone(@PathVariable UUID bookId, @PathVariable int page) {
    return CollectionEntryResponse.from(service.markPageDone(currentMember.id(), bookId, page));
  }

  @DeleteMapping("/{bookId}/pages/{page}")
  public CollectionEntryResponse markPageNotDone(@PathVariable UUID bookId, @PathVariable int page) {
    return CollectionEntryResponse.from(service.markPageNotDone(currentMember.id(), bookId, page));
  }

  /** Sets all three ratings, so a missing or null rating is cleared. */
  @PutMapping("/{bookId}/ratings")
  public CollectionEntryResponse rate(@PathVariable UUID bookId, @RequestBody Ratings ratings) {
    return CollectionEntryResponse.from(
      service.rate(currentMember.id(), bookId, ratings.overall(), ratings.design(), ratings.color()));
  }

  @PutMapping("/{bookId}/notes")
  public CollectionEntryResponse changeNotes(@PathVariable UUID bookId, @RequestBody NotesRequest request) {
    return CollectionEntryResponse.from(service.changeNotes(currentMember.id(), bookId, request.notes()));
  }

  public record OwnedRequest(@NotNull Boolean owned) {
  }

  /** Null clears the answer. */
  public record WantedRequest(@Nullable Wanted wanted) {
  }

  public record ProgressRequest(@NotNull Progress progress) {
  }

  public record NotesRequest(@Nullable String notes) {
  }

  /** A book's overall, design and color ratings, each 0 to 5 or null for not rated. */
  public record Ratings(@Nullable Integer overall, @Nullable Integer design, @Nullable Integer color) {

    static Ratings from(CollectionEntry entry) {
      return new Ratings(entry.getRating(), entry.getDesignRating(), entry.getColorRating());
    }
  }

  /**
   * A member's entry for a book. Its pages done are in order and leave out any beyond the book's page count, which an
   * admin may have lowered after they were marked. The people routes answer with the same shape for anyone's entry.
   */
  public record CollectionEntryResponse(UUID bookId, boolean owned, @Nullable Wanted wanted, Progress progress,
    List<Integer> pagesDone, Ratings ratings, @Nullable String notes, @Nullable LocalDate startedOn,
    @Nullable LocalDate finishedOn, Instant updatedAt) {

    public static CollectionEntryResponse from(TrackedBook tracked) {
      var entry = tracked.entry();
      return new CollectionEntryResponse(entry.getBookId(), entry.isOwned(), entry.getWanted(), entry.getProgress(),
        entry.getPagesDone(tracked.pageCount()), Ratings.from(entry), entry.getNotes(), entry.getStartedOn(),
        entry.getFinishedOn(), entry.getUpdatedAt());
    }
  }
}
