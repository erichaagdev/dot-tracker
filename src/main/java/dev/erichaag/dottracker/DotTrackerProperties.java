package dev.erichaag.dottracker;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The app's own settings under {@code dot-tracker}.
 *
 * @param adminEmails the people who become admins the first time they sign in with Google, without an invite
 */
@ConfigurationProperties("dot-tracker")
public record DotTrackerProperties(@DefaultValue List<String> adminEmails) {
}
