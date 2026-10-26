package dev.erichaag.dottracker.security;

import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import dev.erichaag.dottracker.member.MemberService;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Every {@code /api} route needs a signed-in member and answers 401 without one, so the frontend can show its own
 * sign-in screen. The one exception is {@code GET /api/invites/pending}, which the welcome screen reads before anyone
 * signs in. Everything else, such as the frontend's routes and files, {@code /join}, {@code /oauth2}, {@code /login}
 * and {@code /actuator/health}, is open. People sign in with Google, and the demo profile swaps that for a form login.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

  /**
   * Google sign-in. {@code oauth2Login} finds {@link MemberOidcUserService} as the context's OIDC user service. Google
   * always shows its account chooser, so someone with more than one Google account picks the one their invite is for.
   */
  @Bean
  @Profile("!demo")
  public SecurityFilterChain googleSignIn(HttpSecurity http, ClientRegistrationRepository registrations) {
    var authorizationRequests = new DefaultOAuth2AuthorizationRequestResolver(registrations,
      OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    authorizationRequests.setAuthorizationRequestCustomizer(request -> request
      .additionalParameters(parameters -> parameters.put("prompt", "select_account")));
    return configureCommon(http)
      .oauth2Login(login -> login
        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequests))
        .failureHandler(new GoogleSignInFailureHandler()))
      .build();
  }

  @Bean
  @Profile("demo")
  public SecurityFilterChain demoSignIn(HttpSecurity http) {
    return configureCommon(http)
      .formLogin(Customizer.withDefaults())
      .build();
  }

  /** Lets every member sign in with their email and the password "demo", named after their member id. */
  @Bean
  @Profile("demo")
  public UserDetailsService demoMembers(MemberService members) {
    return email -> members.findByEmail(email)
      .map(member -> User.withUsername(Objects.requireNonNull(member.getId()).toString())
        .password("{noop}demo")
        .roles(member.getRole().name())
        .build())
      .orElseThrow(() -> new UsernameNotFoundException("No member has the email " + email));
  }

  private static HttpSecurity configureCommon(HttpSecurity http) {
    var api = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
    return http
      .authorizeHttpRequests(requests -> requests
        .requestMatchers(HttpMethod.GET, "/api/invites/pending").permitAll()
        .requestMatchers(api).authenticated()
        .anyRequest().permitAll())
      .exceptionHandling(exceptions -> exceptions
        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(UNAUTHORIZED), api))
      // Only /api calls need a session, and none is a page to come back to, so signing in always lands on the app
      .requestCache(cache -> cache.disable())
      .csrf(csrf -> csrf.spa())
      .logout(logout -> logout
        .logoutUrl("/api/logout")
        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(NO_CONTENT)));
  }
}
