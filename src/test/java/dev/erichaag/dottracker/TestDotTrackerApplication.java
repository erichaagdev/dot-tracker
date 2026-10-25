package dev.erichaag.dottracker;

import org.springframework.boot.SpringApplication;

/** Runs the app on port 8889 with the demo profile and a throwaway database, through {@code gain bootTestRun}. */
public class TestDotTrackerApplication {

  public static void main(String[] args) {
    SpringApplication.from(DotTrackerApplication::main)
      .with(PostgresTestConfiguration.class)
      .withAdditionalProfiles("demo")
      .run(args);
  }
}
