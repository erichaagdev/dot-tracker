package dev.erichaag.dottracker;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Answers a path the frontend routes itself, such as {@code /books/<id>}, with {@code index.html}, so a shared link or
 * a reload opens the right screen. A path with a file extension is left to the static resources, and {@code /api},
 * {@code /oauth2}, {@code /login}, {@code /join} and {@code /actuator} to their own handlers.
 */
@Controller
public class SpaForwardingController {

  // A path pattern constrains one segment at a time and only allows "**" at its end, so each depth is listed
  private static final String FIRST = "/{first:(?!(?:api|oauth2|login|join|actuator)$)[^.]*}";
  private static final String SECOND = "/{second:[^.]*}";
  private static final String THIRD = "/{third:[^.]*}";

  @GetMapping({FIRST, FIRST + SECOND, FIRST + SECOND + THIRD})
  public String forward() {
    return "forward:/index.html";
  }
}
