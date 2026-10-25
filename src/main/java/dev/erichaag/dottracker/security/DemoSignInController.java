package dev.erichaag.dottracker.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The demo has no Google sign-in, so the sign-in screen's "Continue with Google" lands on the demo's form login
 * instead, and the frontend needs no demo-only code.
 */
@Controller
@Profile("demo")
public class DemoSignInController {

  @GetMapping("/oauth2/authorization/google")
  public String signIn() {
    return "redirect:/login";
  }
}
