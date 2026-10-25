package dev.erichaag.dottracker.member;

import dev.erichaag.dottracker.member.Member.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MemberRepository extends JpaRepository<Member, UUID> {

  Optional<Member> findByGoogleSubject(String googleSubject);

  /** Finds a member by an email already in the form {@link Member#normalizeEmail} gives. */
  Optional<Member> findByEmail(String email);

  List<Member> findAllByOrderByDisplayNameAsc();

  // Locks the rows, so two admins demoting each other at once can't leave the group without one
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<Member> findByRole(Role role);
}
