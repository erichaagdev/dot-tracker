package dev.erichaag.dottracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ImportRuntimeHints;

@ConfigurationPropertiesScan
@SpringBootApplication
@ImportRuntimeHints(PersistenceRuntimeHints.class)
public class DotTrackerApplication {

  public static void main(String[] args) {
    SpringApplication.run(DotTrackerApplication.class, args);
  }
}
