package dev.erichaag.dottracker.invite;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

import dev.erichaag.dottracker.security.CurrentMember;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/invites")
public class InviteRestController {

  private final InviteService service;

  private final CurrentMember currentMember;

  public InviteRestController(InviteService service, CurrentMember currentMember) {
    this.service = service;
    this.currentMember = currentMember;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public List<InviteResponse> open() {
    return service.findOpen().stream().map(InviteResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public InviteResponse create() {
    return InviteResponse.from(service.create(currentMember.id()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void revoke(@PathVariable UUID id) {
    service.delete(id);
  }

  /** An invite with the link to send, built from the address the admin reached the app at. */
  public record InviteResponse(UUID id, String link, Instant createdAt, Instant expiresAt) {

    static InviteResponse from(Invite invite) {
      var link = ServletUriComponentsBuilder.fromCurrentContextPath()
        .path("/join/{code}")
        .buildAndExpand(invite.getCode())
        .toUriString();
      return new InviteResponse(Objects.requireNonNull(invite.getId()), link, invite.getCreatedAt(),
        invite.getExpiresAt());
    }
  }
}
