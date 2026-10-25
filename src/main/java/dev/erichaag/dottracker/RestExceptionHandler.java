package dev.erichaag.dottracker;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.NoSuchElementException;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a rejected argument with 400, a missing item with 404 and a save over a newer copy with 409, as a
 * {@link ProblemDetail} whose {@code detail} the frontend shows as is, so a service's message should read as a plain
 * sentence. Spring's own errors get their problem details from {@code spring.mvc.problemdetails.enabled}, whose
 * handler runs first.
 */
@RestControllerAdvice
public class RestExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail badRequest(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ProblemDetail notFound(NoSuchElementException e) {
    return ProblemDetail.forStatusAndDetail(NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ProblemDetail conflict() {
    return ProblemDetail.forStatusAndDetail(CONFLICT,
      "This changed somewhere else while you had it open. Refresh the page and try again.");
  }
}
