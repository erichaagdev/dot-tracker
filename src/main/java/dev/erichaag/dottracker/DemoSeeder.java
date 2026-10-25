package dev.erichaag.dottracker;

import dev.erichaag.dottracker.member.Member;
import dev.erichaag.dottracker.member.Member.Role;
import dev.erichaag.dottracker.member.MemberRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds the demo profile's members, Ana as an admin and Bea as a member, who sign in with the password "demo". A
 * database that already has members is left as it is.
 */
@Component
@Profile("demo")
public class DemoSeeder implements ApplicationRunner {

  private final MemberRepository members;

  private final Clock clock;

  public DemoSeeder(MemberRepository members, Clock clock) {
    this.members = members;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (members.count() > 0) {
      return;
    }
    var now = clock.instant();
    members.saveAll(List.of(
      new Member("ana@example.com", "Ana", Role.ADMIN, now),
      new Member("bea@example.com", "Bea", Role.MEMBER, now)
    ));
  }
}
