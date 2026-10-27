# CLAUDE.md

Dot Tracker is a web app for a small group of friends to keep track of their Lifelines FlowArt books. The Spring Boot backend is Java 25 under `src/main/java/dev/erichaag/dottracker/`, the Svelte 5 frontend is in `frontend/`, and the data lives in Postgres 18. It runs on Google Cloud, on Cloud Run with Cloud SQL.

`docs/plans/technical-plan.md` is the plan to build from, with the schema, the API, sign-in, the catalog seed, hosting and the build order. `docs/plans/overview.md` is the same plan for a non-technical reader, and `docs/mockups/index.html` shows the main screens at phone and desktop sizes. Read the plan's section for the part you're changing before you start, and open the mockups in a browser before building or changing a screen. When a change alters something the plan describes, update the plan in the same change.

## Who this app is for

The people using it are hobbyists on their phones, not developers. Every screen works on a phone first and grows into the desktop layout. Words on screen are plain and friendly, with no jargon or error codes, and a message about a failure says what happened and what to do next.

## Running it

- Run Gradle through `gain`, as in `gain build`, never through `./gradlew`. It prints a short summary and keeps the full output in `.gain/build-output.log`.
- `gain build` compiles, builds the frontend and runs every test. Work isn't done until it passes.
- Docker has to be running, because tests and local runs use Postgres in containers.
- `gain bootTestRun` runs the app on port 8889 with the demo profile, a throwaway database and three demo members, `ana@example.com` as an admin and `bea@example.com` and `cam@example.com` as members, all with the password `demo`.
- `gain bootRun` runs the app on port 8080 against the Postgres in `compose.yaml`, with a Google OAuth client read from a git-ignored `.env`.
- `npm run dev` in `frontend` serves the frontend with hot reload on port 5173 and proxies to the demo app on port 8889.
- `gain bootBuildImage --imagePlatform=linux/arm64 --imageName=dot-tracker-native:arm64` builds the production native image for this Mac in a few minutes, and "Checking a native image locally" in `docs/deploy.md` runs it against Postgres. The tests and `gain bootTestRun` always run on the JVM.

## The rule behind every backend choice

Where there's a choice, pick the most common Spring Boot idiom over anything bespoke. A model has seen the common idiom many times and gets it right without studying this repo first, while a bespoke pattern has to be relearned in every session. When this file doesn't settle a question, that rule does.

## Backend

- Code is organized by domain, never by layer. Each domain is a package named after its noun, such as `member`, `book` or `collection`, holding its entity, repository, service and controller side by side. No `controller`, `service` or `repository` packages. Cross-cutting code sits in the root package `dev.erichaag.dottracker`, and sign-in code in `security`.
- Every package has a `package-info.java` marked `@NullMarked`. Types are non-null unless annotated `@Nullable` from `org.jspecify.annotations`, and NullAway runs through Error Prone on every compile of the app's own code, so a possible null dereference fails the build. Fix the code rather than suppressing a NullAway or Error Prone finding.
- DTOs, request and response bodies and configuration properties are records. Entities are plain classes.
- Configuration lives in `application.yaml` under `dot-tracker.*` in kebab case and binds to `@ConfigurationProperties` records. Demo overrides go in `application-demo.yaml`.
- Wire beans with stereotypes and constructor injection. A bean that only exists in one mode carries `@Profile("demo")` or `@Profile("!demo")`.
- Inject a `Clock` wherever the current time matters, so tests can fix it. `ClockConfiguration` provides one in `America/Chicago`.
- Production runs as a GraalVM native image. Profiles and conditions are fixed when it's built, so the demo beans aren't in it, and a property set at startup can change a value but never which beans exist.
- A native image reaches a class through reflection or serialization only when a hint names it. Anything new kept in the session gets a serialization hint in `security/SessionRuntimeHints`, and anything else that Spring and the library leave out goes in a `RuntimeHintsRegistrar` like `PersistenceRuntimeHints`. The JVM tests can't catch a missing hint, so check such a change by building the image and running it.

## Persistence

- Every schema change is a Flyway migration, `V<n>__<description>.sql` in `src/main/resources/db/migration`, the only place SQL is written. An applied migration never changes, so a missed column or seed row goes into a new migration too. Hibernate runs with `ddl-auto=validate`, so an entity that disagrees with its table stops the app at startup.
- Every table's primary key is `id uuid primary key default uuidv7()`, and entities map it as `@Id @UuidGenerator(style = VERSION_7) private @Nullable UUID id`. Never key or reference a row by a name or any other value a person can change.
- Every top-level entity has `@Version private @Nullable Long version`, so a stale copy saved over a newer one fails with `ObjectOptimisticLockingFailureException`.
- Entities have a protected no-argument constructor and guard their own invariants, such as trimming names, range-checking ratings and keeping finished pages sorted and within the page count, so no caller can break them. Change state through entity methods and let the service's transaction flush it.
- An entity refers to another entity by its id, as a plain `UUID` field such as `Book.productLineId` or `Invite.createdBy`, never through `@ManyToOne`. Each service loads what it needs inside its own transaction, so nothing loads lazily after the transaction ends.
- A value that belongs to one parent and has no identity of its own, such as a book's listing, is an `@Embeddable` record in an `@ElementCollection(fetch = EAGER)` list with `@OrderColumn`.
- Services own transactions with `@Transactional`, and read-only methods use `@Transactional(readOnly = true)`. Controllers call services, never repositories, and entities never leave the service layer.
- `spring.jpa.open-in-view` is off, so everything a response needs is loaded inside the service's transaction.

## REST API

- Controllers live under `/api` with plural resources and kebab-case multi-word segments. Ids in paths and bodies are UUID strings.
- Request and response records nest inside the controller that uses them, and each response record has a static `from` that maps the entity. A controller method is a call to the service plus that mapping.
- Admin routes carry `@PreAuthorize("hasRole('ADMIN')")`. Controllers get the signed-in member from `CurrentMember`, never from the request.

## Errors and logging

- Don't define custom exception types. Services throw `IllegalArgumentException` for a rejected argument and `NoSuchElementException` for a missing item, each with a plain sentence a person can read. `RestExceptionHandler` turns them into a 400 or 404 `ProblemDetail` whose `detail` the frontend shows as is. It also turns Spring Security's `AccessDeniedException` into a 403 saying only an admin can do that, and an `ObjectOptimisticLockingFailureException` into a 409 that asks the person to refresh. Controllers never catch exceptions.
- Log through SLF4J with `private static final Logger logger = LoggerFactory.getLogger(Xxx.class)`, using placeholders rather than string concatenation.
- Comment only the reason for a decision that isn't obvious.

## Backend tests

- JUnit and AssertJ. Test classes are named after the class under test, test methods are camel-case sentences such as `markingTheLastPageFinishesTheBook`, and each test marks its `// Given`, `// When` and `// Then` sections, with `// When / Then` for a single step.
- Tests run against real Postgres 18 through Testcontainers, never H2 or another stand-in. Repositories and services use `@DataJpaTest` with `@AutoConfigureTestDatabase(replace = NONE)` and `@Import(PostgresTestConfiguration.class)`, with the service under test added through `@Import`, so the real Flyway migrations run.
- Every controller gets a `@WebMvcTest` with MockMvc, the service as a `@MockitoBean` and the real `SecurityConfiguration` imported. These tests pin the contract the frontend relies on, such as field names, status codes, the 401 without a session, the 403 for a member on an admin route and the CSRF requirement on writes.
- Entity rules get plain unit tests. Build test data with small private factory methods.
- Every behavior change comes with a test that fails without it.

## Frontend

- Svelte 5 with runes, plain JavaScript and Vite, with `@lucide/svelte` as the only dependency beyond the toolchain. No TypeScript, UI kit or CSS framework.
- All backend calls go through `src/lib/api.js`. Shared state lives in `src/lib/*.svelte.js` stores, and components call store functions instead of assigning to store objects.
- Styling is plain global CSS in `src/styles/`, where each partial owns its class prefixes and every color, size and font comes from `tokens.css`, which defines both the light and the dark theme. No `<style>` blocks.
- Below 900 px wide the app is one column with a bottom tab bar, and from 900 px a top bar replaces it. Check every screen at 375 px and at desktop width, and never let a page scroll sideways.
- Every control has a visible text label, touch targets are at least 44 px, and every status is an icon, a word and a color. The member's initials at the top right of a phone screen are the one control without a word, a link to Account named "Your account" for screen readers. Icons are per-icon imports with `aria-hidden="true"`.
- A change applies on screen at once and then sends its request, and a refused request puts things back and shows why. Nothing asks "are you sure" except deleting and changing someone's role.
- Browser storage goes through `src/lib/storage.js`, which tolerates storage being blocked.
- `src/sw.js` is a service worker that opens the app from a cache while the backend starts. It never caches anything from `/api`, and the paths it leaves to the backend are the ones `SpaForwardingController` doesn't forward, so a change to one changes the other.

## Writing

Comments, docs and every string on screen are plain, concise prose. Use no em dashes, and write periods, commas or short sentences instead. Describe things as they are now, and never leave text about what something used to be or that it was a fix.

## Dependencies

Every version comes from the Spring Boot BOM except the few pinned in `gradle/libs.versions.toml`, and each is the latest stable release, never a milestone or release candidate. Add a dependency only when the plan calls for it or nothing already in the stack covers the need.

## Git

Commit, push or open a pull request only when asked. A push to `main` deploys to production through `.github/workflows/deploy.yml`.
