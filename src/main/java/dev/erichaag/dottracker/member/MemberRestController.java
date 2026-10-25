package dev.erichaag.dottracker.member;

import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.security.CurrentMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MemberRestController {

  private final MemberService service;

  private final CurrentMember currentMember;

  public MemberRestController(MemberService service, CurrentMember currentMember) {
    this.service = service;
    this.currentMember = currentMember;
  }

  @GetMapping("/me")
  public MemberResponse me() {
    return MemberResponse.from(service.getById(currentMember.id()));
  }

  @GetMapping("/members")
  @PreAuthorize("hasRole('ADMIN')")
  public List<MemberResponse> members() {
    return service.findAll().stream().map(MemberResponse::from).toList();
  }

  @PutMapping("/members/{id}/role")
  @PreAuthorize("hasRole('ADMIN')")
  public MemberResponse changeRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
    return MemberResponse.from(service.changeRole(id, request.role()));
  }

  public record MemberResponse(UUID id, String email, String displayName, Role role, Instant joinedAt) {

    static MemberResponse from(Member member) {
      return new MemberResponse(Objects.requireNonNull(member.getId()), member.getEmail(), member.getDisplayName(),
        member.getRole(), member.getJoinedAt());
    }
  }

  public record RoleRequest(@NotNull Role role) {
  }
}
