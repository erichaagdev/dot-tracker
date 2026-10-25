package dev.erichaag.dottracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = RestExceptionHandlerTest.ThrowingController.class)
@Import(RestExceptionHandlerTest.ThrowingController.class)
class RestExceptionHandlerTest {

  @Autowired
  MockMvc mvc;

  @Test
  void aRejectedArgumentAnswers400WithTheMessageAsDetail() throws Exception {
    // When / Then
    mvc.perform(get("/test/rejected"))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.status").value(400))
      .andExpect(jsonPath("$.detail").value("A rating must be between 0 and 5."));
  }

  @Test
  void aMissingItemAnswers404WithTheMessageAsDetail() throws Exception {
    // When / Then
    mvc.perform(get("/test/missing"))
      .andExpect(status().isNotFound())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.status").value(404))
      .andExpect(jsonPath("$.detail").value("That book isn't in the catalog anymore."));
  }

  @Test
  void aSaveOverANewerCopyAnswers409AskingThePersonToRefresh() throws Exception {
    // When / Then
    mvc.perform(get("/test/stale"))
      .andExpect(status().isConflict())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.status").value(409))
      .andExpect(jsonPath("$.detail")
        .value("This changed somewhere else while you had it open. Refresh the page and try again."));
  }

  @RestController
  static class ThrowingController {

    @GetMapping("/test/rejected")
    String rejected() {
      throw new IllegalArgumentException("A rating must be between 0 and 5.");
    }

    @GetMapping("/test/missing")
    String missing() {
      throw new NoSuchElementException("That book isn't in the catalog anymore.");
    }

    @GetMapping("/test/stale")
    String stale() {
      throw new ObjectOptimisticLockingFailureException(Object.class, UUID.randomUUID());
    }
  }
}
