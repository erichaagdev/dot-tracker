package dev.erichaag.dottracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.member.MemberRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

// The seeder runs by hand inside the test's transaction, so its members don't outlive the test
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({PostgresTestConfiguration.class, FixedClockConfiguration.class})
class DemoSeederTest {

  @Autowired
  MemberRepository members;

  @Autowired
  Clock clock;

  @Test
  void anaJoinsAsAnAdminAndBeaAsAMember() {
    // When
    seed();

    // Then
    assertThat(members.findAllByOrderByDisplayNameAsc())
      .extracting(Member::getEmail, Member::getDisplayName, Member::getRole)
      .containsExactly(
        tuple("ana@example.com", "Ana", Role.ADMIN),
        tuple("bea@example.com", "Bea", Role.MEMBER)
      );
  }

  @Test
  void aDatabaseThatAlreadyHasMembersIsLeftAsItIs() {
    // Given
    members.save(new Member("cat@example.com", "Cat", Role.ADMIN, FixedClockConfiguration.NOW));

    // When
    seed();

    // Then
    assertThat(members.findAll()).extracting(Member::getEmail).containsExactly("cat@example.com");
  }

  private void seed() {
    new DemoSeeder(members, clock).run(new DefaultApplicationArguments());
  }
}
