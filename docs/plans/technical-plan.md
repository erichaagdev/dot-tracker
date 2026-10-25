# Dot Tracker technical plan

Dot Tracker is a web app for a small group of friends who collect Lifelines FlowArt books. It holds one shared catalog of every book, and each person keeps their own record against it: whether they own a book or want it, how far along they are, which pages they've finished and how they rated it. It runs as one Spring Boot service with a Svelte frontend and a Postgres database, works on phones and desktops, and is reachable over HTTPS so friends can use it from anywhere.

This is the plan to build from. `CLAUDE.md` at the repository root holds the conventions every change follows. `docs/plans/overview.md` is the same plan for a non-technical reader, and the mockups in `docs/mockups/index.html` show every first-version screen at phone and desktop sizes. Open the mockups in a browser before building any screen.

## Scope

The first version covers the catalog seeded from the spreadsheet in `inventory/`, sign-in by invitation, and each person's own tracking of what they own and want, their progress, finished pages, ratings and notes. It also covers browsing one running list with search and filters, a home screen of what you're working on, and admin screens to add and edit books and invite friends.

Each person sees only their own data. Seeing what others in the group own and how they rated a book isn't needed to start, and neither is finding new books on the web, since the catalog is small enough to keep by hand. "Later work" at the end describes both, along with release years and Facebook sign-in.

## The one modeling decision everything hangs on

The spreadsheet mixes two kinds of data in one row, and the app keeps them apart. The catalog is shared and describes the book itself: its product line, name, page count, where it's sold and for how much, whether it's a store exclusive and whether it can still be bought. A collection entry belongs to one person and one book, and holds that person's state: owned, wanted, progress, finished pages, ratings and notes. The spreadsheet's "Want to Collect?" and "Purchased" columns are personal to whoever made it, so the app ignores them. Its "Availability Status" column is mostly catalog data, except that "Purchased" appears there too, which is personal and is ignored for availability.

## Stack and versions

Every runtime and library is on its latest stable release as of October 2026. Postgres 19 and Spring Boot 4.2 were still at release candidates on that date, so the plan pins the current GA lines and bumps once they ship.

| Piece | Version | Notes |
|---|---|---|
| Java | 25 | Latest LTS, through a Gradle toolchain |
| Gradle | 9.8.2 | Already in the wrapper. Kotlin DSL with a version catalog |
| Spring Boot | 4.1.2 | BOM and Gradle plugin. Brings Spring Framework 7.0, Spring Security 7.1, Spring Session 4.1, Hibernate 7.4, Flyway 12.4, Jackson 3.1, JUnit 6 and Testcontainers 2.0 |
| PostgreSQL | 18 | `postgres:18` image locally and in tests, and a managed 18 in production |
| Error Prone | 2.51.0 | With the `net.ltgt.errorprone` plugin 5.1.1 |
| NullAway | 0.14.3 | JSpecify mode, `@NullMarked` packages |
| foojay resolver | 1.0.0 | Settings plugin for the toolchain download |
| Node | 24 LTS | Node 26 is due to enter LTS at the end of October 2026, so move to it then |
| Svelte | 5.57 | Runes, plain JavaScript |
| Vite | 8.3 | With `@sveltejs/vite-plugin-svelte` 7.3 |
| Lucide | 1.61 | `@lucide/svelte`, the only frontend dependency beyond the toolchain |
| Cloud SQL Postgres socket factory | 1.30.2 | `com.google.cloud.sql:postgres-socket-factory`, connects Cloud Run to Cloud SQL in production |

## Repository layout

```
build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml
CLAUDE.md                         Conventions every change follows
compose.yaml                      Postgres 18 for local runs
inventory/                        The original spreadsheet tabs, plus catalog.csv once curated
docs/plans/                       This plan and the overview
docs/mockups/index.html           Screen mockups
src/main/java/dev/erichaag/dottracker/
  DotTrackerApplication.java, DotTrackerProperties.java, DemoSeeder.java
  ClockConfiguration.java, RestExceptionHandler.java, SpaForwardingController.java
  security/                       SecurityConfiguration, MemberOidcUserService, MemberOidcUser, CurrentMember, JoinController
  member/                         Member, its repository, service and controller
  invite/                         Invite, its repository, service and controller
  book/                           ProductLine, Book, Listing, Availability, service, controller
  collection/                     CollectionEntry, Progress, Wanted, service, controller
src/main/resources/
  application.yaml, application-demo.yaml
  db/migration/                   V1__schema.sql, V2__catalog.sql
src/test/java/dev/erichaag/dottracker/...
src/test/java/dev/erichaag/dottracker/TestDotTrackerApplication.java
frontend/                         Svelte app, built into the jar's static resources
```

## Build

The build follows the shape of `~/Projects/erichaagdev/meeting-assistant/build.gradle.kts`. The `java` plugin applies by its id, and `org.springframework.boot` and `net.ltgt.errorprone` through catalog aliases. Every dependency version comes from the Spring Boot BOM, imported as a `platform`, except Error Prone, NullAway and the Cloud SQL socket factory. Error Prone runs on every compile with NullAway at `ERROR`, `OnlyNullMarked` and `JSpecifyMode`.

The catalog's libraries are these starters and modules.

- `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`
- `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql`
- `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-client`, `spring-boot-starter-session-jdbc`
- `org.jspecify:jspecify`
- `com.google.cloud.sql:postgres-socket-factory` as `runtimeOnly`, used only when the datasource URL names it
- `spring-boot-docker-compose` as `developmentOnly`
- For tests, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql`, `testcontainers-junit-jupiter`, AssertJ, JUnit Jupiter and the platform launcher

Two `Exec` tasks build the frontend. `npmInstall` runs `npm ci` in `frontend`, and `buildFrontend` runs `npm run build` into `build/frontend/static`. `build/frontend` is added as a main resources directory, so the built app lands in the jar's `static` folder and is served from `/`. Both run npm through `sh -c`, so it's found on the PATH of the shell that started the build, where mise puts the pinned Node, rather than on whatever PATH the Gradle daemon started with. Both declare their inputs and outputs so Gradle skips them when nothing changed.

`bootBuildImage` produces the production container image with `BP_JVM_VERSION=25` set on the task, so there is no Dockerfile. Run every Gradle command through `gain`, as in `gain build` or `gain bootRun`.

## Conventions

`CLAUDE.md` holds the backend, frontend and testing conventions, and every change follows them. This plan only adds what's specific to each part of the app.

## Data model

`V1__schema.sql` creates these tables, plus the two Spring Session tables copied from Spring Session's `schema-postgresql.sql`, with `spring.session.jdbc.initialize-schema=never` so Flyway owns them.

Every table's primary key is a UUID with no meaning of its own, so fixing a name or any other value is a one-row update that nothing else has to follow. Entities generate UUIDv7 ids through Hibernate's `@UuidGenerator(style = VERSION_7)`, whose time-ordered values keep index inserts local. Each column also defaults to Postgres 18's `uuidv7()`, so `V2__catalog.sql` can insert rows without making up ids.

```sql
create table member (
  id uuid primary key default uuidv7(),
  google_subject text unique,
  email text not null,
  display_name text not null,
  role text not null check (role in ('ADMIN', 'MEMBER')),
  joined_at timestamptz not null,
  version bigint not null
);
create unique index member_email on member (lower(email));

create table invite (
  id uuid primary key default uuidv7(),
  code text not null unique,
  created_by uuid not null references member (id),
  created_at timestamptz not null,
  expires_at timestamptz not null,
  accepted_by uuid references member (id),
  accepted_at timestamptz,
  version bigint not null
);

create table product_line (
  id uuid primary key default uuidv7(),
  name text not null unique,
  default_page_count integer not null check (default_page_count > 0),
  sort_order integer not null,
  version bigint not null
);

create table book (
  id uuid primary key default uuidv7(),
  product_line_id uuid not null references product_line (id),
  name text not null,
  also_known_as text[] not null default '{}',
  availability text not null check (availability in ('AVAILABLE', 'COMING_SOON', 'RETIRED', 'UNKNOWN')),
  page_count integer not null check (page_count > 0),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  version bigint not null
);
create unique index book_line_name on book (product_line_id, lower(name));

create table book_listing (
  book_id uuid not null references book (id) on delete cascade,
  position integer not null,
  retailer text not null,
  price_cents integer check (price_cents >= 0),
  url text,
  exclusive boolean not null default false,
  primary key (book_id, position)
);

create table collection_entry (
  id uuid primary key default uuidv7(),
  member_id uuid not null references member (id) on delete cascade,
  book_id uuid not null references book (id) on delete cascade,
  owned boolean not null default false,
  wanted text check (wanted in ('YES', 'MAYBE', 'NO')),
  progress text not null default 'NOT_STARTED' check (progress in ('NOT_STARTED', 'IN_PROGRESS', 'FINISHED')),
  pages_done integer[] not null default '{}',
  rating smallint check (rating between 0 and 5),
  design_rating smallint check (design_rating between 0 and 5),
  color_rating smallint check (color_rating between 0 and 5),
  notes text,
  started_on date,
  finished_on date,
  updated_at timestamptz not null,
  version bigint not null,
  unique (member_id, book_id)
);
```

The unique index on product line and name, ignoring case, only stops the same book from being entered twice. Nothing references it, and the same name can appear in two lines, as "Animals" does in Color to Reveal and Dot by Letter Mini. `also_known_as` holds other names a book goes by, such as "Snowfall Serenity" for "Snowflake Serenity", and search matches on it.

A listing is an `@Embeddable` record `Listing(String retailer, @Nullable Integer priceCents, @Nullable String url, boolean exclusive)` in an `@ElementCollection(fetch = EAGER)` with `@OrderColumn(name = "position")`. Retailers are free text, and the book editor suggests the names already in use. A book is a store exclusive when any of its listings is exclusive.

`also_known_as` and `pages_done` map to `List<String>` and `List<Integer>` fields, which Hibernate 7 stores as Postgres arrays. `rating` is the overall score for how much someone liked the book, and it's the one lists and the shelf show. `design_rating` and `color_rating` are optional extras on the book screen. A null rating means not rated yet, and 0 is a real score.

## Collection rules

`CollectionEntry` holds these rules, and `CollectionService` creates an entry the first time a person changes anything about a book.

- Marking a book in progress or finished also marks it owned. Unmarking owned leaves progress alone, so a finished book someone gave away keeps its history.
- Marking a page done on a book that hasn't been started moves it to in progress and sets `started_on`.
- Marking the last remaining page done moves it to finished and sets `finished_on`. Unmarking a page on a finished book moves it back to in progress and clears `finished_on`.
- Setting progress by hand never changes pages, so someone can finish a book without ticking every page.
- A page number must be between 1 and the book's page count. If an admin later lowers a page count, pages beyond it stay stored but are left out of responses and counts.
- Ratings are 0 to 5 or null. `wanted` is independent of `owned`, and the wishlist is every book marked wanted yes or maybe that isn't owned.

## REST API

Every route lives under `/api`, uses plural resources and kebab-case segments, and answers JSON. Ids in paths and bodies are UUID strings. Instants are ISO 8601 strings and dates are ISO dates. A request without a session gets a 401, never a redirect, so the frontend can show the sign-in screen. The one route open without a session is `GET /api/invites/pending`, which the welcome screen reads before anyone signs in. A member on an admin route gets a 403 `ProblemDetail` whose `detail` is "Only an admin can do that.", from `RestExceptionHandler` handling Spring Security's `AccessDeniedException`.

| Method and path | Who | What it does |
|---|---|---|
| `GET /api/me` | member | The signed-in member, with their id, email, display name, role and when they joined |
| `POST /api/logout` | member | Ends the session |
| `GET /api/books` | member | The whole catalog with listings. About a hundred rows, so the frontend filters and searches locally |
| `GET /api/books/{id}` | member | One book |
| `POST /api/books` | admin | Adds a book |
| `PUT /api/books/{id}` | admin | Edits a book |
| `DELETE /api/books/{id}` | admin | Removes a book added by mistake. Refused once anyone has an entry for it, in favor of marking it retired |
| `GET /api/product-lines` | member | Product lines in sort order |
| `POST /api/product-lines`, `PUT /api/product-lines/{id}` | admin | Adds or edits a product line |
| `GET /api/collection` | member | Every entry of the signed-in member |
| `PUT /api/collection/{bookId}/owned` | member | Body `{"owned": true}` |
| `PUT /api/collection/{bookId}/wanted` | member | Body `{"wanted": "MAYBE"}`, or null to clear |
| `PUT /api/collection/{bookId}/progress` | member | Body `{"progress": "IN_PROGRESS"}` |
| `PUT /api/collection/{bookId}/pages/{page}` | member | Marks a page done |
| `DELETE /api/collection/{bookId}/pages/{page}` | member | Marks a page not done |
| `PUT /api/collection/{bookId}/ratings` | member | Body `{"overall": 4, "design": 5, "color": null}` |
| `PUT /api/collection/{bookId}/notes` | member | Body `{"notes": "..."}` |
| `GET /api/members` | admin | Everyone in the group |
| `PUT /api/members/{id}/role` | admin | Body `{"role": "ADMIN"}` or `"MEMBER"`. Refused for the group's last admin |
| `GET /api/invites`, `POST /api/invites` | admin | Lists open invites newest first, or creates one and answers 201. Each invite has its id, its full `/join/<code>` link built from the address the admin used, and when it was made and expires |
| `DELETE /api/invites/{id}` | admin | Revokes an invite, and answers 204 even when it's already gone |
| `GET /api/invites/pending` | anyone | `{"expiresAt": ...}` for the open invite held in the session, or a 404 saying the invite has expired or was already used |
| `GET /join/{code}` | anyone | Starts sign-in with an invite, outside `/api` |

Every collection write answers with the updated entry, and every write is idempotent, so a retried request does no harm. Admin routes carry `@PreAuthorize("hasRole('ADMIN')")` with `@EnableMethodSecurity`.

## Sign-in and membership

People sign in with Google through `oauth2Login`, and nobody gets in without an invitation. A closed group of friends doesn't need passwords, password resets or email sending. Not everyone in the group may have a Google account, so "Later work" covers Facebook as a second way in. The consent screen asks only for `openid`, `email` and `profile`, which lets the Google client be published to production without app verification.

1. An admin creates an invite in the app and texts its link, `https://<host>/join/<code>`, to a friend. A code is 16 random bytes from `SecureRandom`, encoded as URL-safe Base64, works once and expires 14 days after it was made by the injected `Clock`.
2. `GET /join/{code}`, in `security/JoinController`, checks the code, keeps it in the session and redirects to the frontend's `/welcome` screen. An expired, used or unknown code redirects to `/?signin=invite-expired` instead. The welcome screen, from the mockups' Joining screen, reads the invite's expiry from `GET /api/invites/pending`, which `JoinController` also serves, and its "Continue with Google" button links to `/oauth2/authorization/google`.
3. `MemberOidcUserService` extends `OidcUserService`, and `oauth2Login` finds it as the context's OIDC user service bean. After Google answers, it hands the account to `MemberService.signInWithGoogle` in one transaction. That finds the member by Google subject, then by email, which links the Google account to them, and otherwise accepts the invite held in the session and creates the member, with the display name from Google's `name` claim, or the email when it's missing. An email listed in `dot-tracker.admin-emails` becomes an admin on first sign-in without an invite, which is how the first account exists. Matching by email and the admin bootstrap both need Google's `email_verified` claim, and a member already linked to one Google account is never linked to another.
4. Anyone else is refused with an `OAuth2AuthenticationException`, and the failure URL is `/?signin=not-invited`, where the sign-in screen asks them for an invite link. Every other failed Google sign-in lands there too.

`Invite` keeps `created_by` and `accepted_by` as plain member ids rather than entity references. `MemberService` refuses to make the group's last admin a member, with a plain sentence, and locks the admin rows while it checks, so two admins demoting each other at once can't leave the group without one. A session holds the role its member signed in with, so a change of role deletes that member's sessions through `FindByIndexNameSessionRepository.findByPrincipalName` with their id. A demoted admin loses admin rights at once, and a promoted member signs in again to get them.

The signed-in user is named after their member id under either sign-in. `MemberOidcUserService` answers with a `MemberOidcUser`, a `DefaultOidcUser` whose name is the member id and whose only authority is `ROLE_ADMIN` or `ROLE_MEMBER`. The demo profile's `UserDetailsService` finds a member by the email typed into the form and answers with a user named after their id. `CurrentMember` reads the id from the security context's authentication name, so controllers work the same under both. The request cache is off, since only `/api` calls need a session and none of them is a page to come back to, so signing in always lands on `/`.

Sessions live in Postgres through Spring Session JDBC, last 90 days and survive restarts and deploys. The session cookie is `Secure`, `HttpOnly` and `SameSite=Lax`, with `server.servlet.session.cookie.max-age` set so a phone keeps it after the browser closes. The demo profile turns `Secure` off, since it runs on plain `http://localhost`, where some browsers won't keep a `Secure` cookie.

Google's client id and secret come from the `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` environment variables, which `application.yaml` names as placeholders in `spring.security.oauth2.client.registration.google`, and the admin emails from `DOT_TRACKER_ADMIN_EMAILS` the same way. Locally, `spring.config.import=optional:file:.env[.properties]` reads them from a git-ignored `.env` in the same `NAME=value` form. Spring Boot leaves a placeholder it can't resolve as written, so without them the Google registration still exists, with a client that can't sign anyone in, and tests, `ApplicationContextTest` and the demo profile all start without Google credentials.

CSRF protection uses `http.csrf(csrf -> csrf.spa())`, which `CsrfConfigurer` has in Spring Security 7.1.2. It sets an `XSRF-TOKEN` cookie on every response that doesn't already carry one, which `api.js` reads and sends back as `X-XSRF-TOKEN` on every write. `POST /api/logout` ends the session and answers 204. Behind Cloud Run's front end, `server.forward-headers-strategy=framework` makes the OAuth redirect URI use `https`.

## Seeding the catalog from the spreadsheet

The six CSVs in `inventory/` are exports of the spreadsheet's tabs. The main tab holds every book, so it is the source for the catalog, which comes to 93 books across 16 product lines. The seasonal tabs hold no book that the main tab lacks, and the app has no seasons, so they only contribute the few listings that the main tab doesn't have. The Miscellaneous tab holds candle diffusers, a journal set and a digital detox kit, which aren't books, so they stay out.

Curating is a one-time step, and its output is `inventory/catalog.csv` with one row per book and the columns product line, name, also known as, availability, page count and listings. It applies these rules.

- The natural key is product line plus name. The Winter tab's first header reads `Column 1` instead of `Type` and is read as `Type`.
- Spelling fixes become the name, with the spreadsheet's spelling kept in `also_known_as` so search still finds it. These are "Pumpins & Pastures" to "Pumpkins & Pastures", "Museum Materpieces" to "Museum Masterpieces" and "Foilage and Flowers" to "Foliage and Flowers". "Lovestruck/Made with Love" becomes "Lovestruck", also known as "Made with Love" and "Lovestruck/Made with Love".
- Two pairs of rows are one book each. The Dot by Letter Mini "Snowfall Serenity" is "Snowflake Serenity", and the Color to Reveal "Animals (Updated Version)" is "Animals". Each pair becomes one book under the name the Lifelines website uses, with the other name in `also_known_as`.
- Each distinct website and price for a book becomes a listing, so the three Fall books that the Fall tab lists at Game Nerdz and the main tab lists at Lifelines get one listing for each. Prices are stored in cents.
- Availability maps "Available on Lifelines" to `AVAILABLE`, "Coming Soon" to `COMING_SOON`, "Not Available" to `RETIRED`, and blank or "Purchased" to `UNKNOWN`. Two books override the mapping. Lovestruck is `RETIRED`, since it's gone from the Lifelines website, and Animals is `AVAILABLE`, since it's on it.
- Page count is 25 for Dot by Letter Large Dot and 20 for every other line, held in each product line's `default_page_count`. An admin can give a single book its own count in the app.
- Corner Cafe, Cozy Companions and Cabin Getaway are Target exclusives. Corner Cafe and Cozy Companions already list Target, and that listing is marked exclusive. Cabin Getaway gets an exclusive Target listing beside its Turner Toys one.

`V2__catalog.sql` inserts the product lines, books and listings from `inventory/catalog.csv`. That migration never changes after it's applied, and every later catalog change goes through the app. `CatalogSeedTest` checks the migrated counts per product line and that every row of every inventory CSV outside the Miscellaneous tab matches a book by name or by an `also_known_as` entry.

The spreadsheet is a public one found online, so its personal columns seed nothing, and everyone starts with an empty shelf.

## Frontend

The frontend is one Svelte 5 app in plain JavaScript, built by Vite, and served by Spring Boot from the same origin, so cookies and CSRF need no cross-origin setup.

- Screens are Shelf, Browse, Book, Wishlist, Account, Book editor, Group, Sign in and Welcome, which an invite link lands on at `/welcome`. The mockups show Shelf, Browse, Book, the book editor and the invite's welcome screen at phone width, and Browse with a book open at desktop width. Wishlist reuses Browse's rows, and Account and Group are short lists in the same style.
- Below 900 px wide the app is one column with a bottom tab bar of Shelf, Browse, Wishlist and Account, and a book opens as its own screen. From 900 px a top bar replaces the tab bar and holds the admin links, and Browse puts the filters on the left, the list in the middle and the selected book's detail on the right, so a desktop user never leaves the list.
- `src/lib/router.svelte.js` keeps routes in the path, such as `/books/<id>`, so a link to a book can be shared. `SpaForwardingController` forwards any `GET` without a file extension outside `/api`, `/oauth2`, `/login`, `/join` and `/actuator` to `index.html`.
- `src/lib/api.js` adds the CSRF header to every write, shows a refused request's `ProblemDetail.detail` as the message and sends a 401 to the sign-in screen.
- `src/lib/catalog.svelte.js` loads the books and product lines once. `src/lib/collection.svelte.js` loads the member's entries and owns every change to them.
- Changes apply on screen at once and then send their request. Requests for the same book go one after another, so quick taps on the page grid never race each other's `@Version`. A refused request puts the entry back and shows why.
- Browse is one list of every book, grouped by product line. Filters and search are remembered per device through `src/lib/storage.js`.
- Lists, the shelf and the desktop table show only the overall rating. The book screen shows it first, with the design and color ratings below it.
- The mockups' `:root` block is the starting token set for `src/styles/tokens.css`, light and dark. Fonts are Bricolage Grotesque for headings and Atkinson Hyperlegible Next for everything else, vendored as `woff2` files rather than loaded from Google.
- A web app manifest with icons and `theme-color` lets people add the app to their home screen, where it opens without browser chrome. There's no service worker, since the app needs the network anyway.

## Local development

Docker Desktop has to be running for local runs and for tests. `compose.yaml` defines a `postgres:18` service with a named volume, and `spring-boot-docker-compose` starts it on `gain bootRun`, so the app runs on port 8080 against a database that persists between runs. Running it this way needs a Google OAuth client with `http://localhost:8080/login/oauth2/code/google` as a redirect URI, and a git-ignored `.env` at the root with `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` and your email as `DOT_TRACKER_ADMIN_EMAILS`, one `NAME=value` per line without quotes.

`gain bootTestRun` starts `TestDotTrackerApplication`, which runs the app through `SpringApplication.from(...).with(...)` with a throwaway Testcontainers Postgres and the `demo` profile on port 8889. The demo profile, in `application-demo.yaml` and the `@Profile("demo")` beans, swaps Google sign-in for Spring Security's form login and its default page at `/login`, with two members, `ana@example.com` as an admin and `bea@example.com` as a member, both with the password `demo`. Its `DemoSeeder` saves the two members through the repository when there are no members yet, and gives each of them a believable collection with a book in progress, ratings and a wishlist, so every screen has something on it.

`npm run dev` in `frontend` serves the app with hot reload on port 5173 and proxies `/api`, `/join`, `/login`, `/oauth2` and `/logout` to port 8889 by default, or to whatever `DOT_TRACKER_API_URL` names. Gradle's toolchain provides Java 25, and a `mise.toml` at the root pins Node 24 for mise.

## Testing

- Entity rules, such as the collection rules above, get plain unit tests.
- Repositories and services use `@DataJpaTest` with `@AutoConfigureTestDatabase(replace = NONE)` and `@Import(PostgresTestConfiguration.class)`, whose `@ServiceConnection` bean starts one `postgres:18` container that every test reuses. The real Flyway migrations run, so a test sees the seeded catalog. The service under test is added with `@Import`.
- Every controller gets a `@WebMvcTest` with MockMvc, the service as a `@MockitoBean` and the real `SecurityConfiguration` imported. Those tests pin JSON field names, status codes, the 401 for a missing session, the 403 for a member on an admin route and the CSRF requirement on writes, using `oidcLogin()` and `csrf()` from `spring-security-test`. `SecurityConfigurationTest` covers the rules that belong to no controller, such as signing out, the not-invited redirect and the demo form login, and sends the CSRF token the way the frontend does, from the `XSRF-TOKEN` cookie into the `X-XSRF-TOKEN` header, since `csrf()` swaps the context's token repository for one that sets no cookie.
- `MemberOidcUserServiceTest` covers each sign-in path, being an existing member by Google subject, an existing member by email whose Google account gets linked, accepting a valid invite, refusing an expired or used one, bootstrapping an admin, refusing an unverified email and refusing a stranger.
- `MemberServiceTest` imports Spring Session's `JdbcHttpSessionConfiguration`, the configuration Spring Boot imports for the app, and checks that a change of role deletes that member's sessions in Postgres and leaves everyone else's.
- `CatalogSeedTest` checks the seed as described above.
- `ApplicationContextTest` starts the whole app once against the test container.

## Hosting and deployment

The app runs on Google Cloud, in one project in `us-central1`.

- Cloud Run runs the container image that `gain bootBuildImage` builds, pulled from Artifact Registry. The task sets `imagePlatform = "linux/amd64"`, since Cloud Run runs amd64 and a Mac builds arm64 by default. The service gets 512 MB of memory, startup CPU boost and between 0 and 2 instances. Scaling to zero keeps it free, at the cost of a few seconds' wait on the first visit after a quiet spell, and a minimum of 1 instance removes that wait for roughly $10 more a month. Sessions live in Postgres, so any instance serves any request.
- Cloud SQL for PostgreSQL 18 holds the data, on the smallest shared-core tier with automated daily backups. It's created with `--edition=ENTERPRISE --tier=db-f1-micro`, because new Postgres instances otherwise default to the much pricier Enterprise Plus edition.
- The app reaches Cloud SQL through the Cloud SQL Java connector, which handles TLS and authorizes through the Cloud Run service account's `roles/cloudsql.client`, so the instance needs no authorized networks. The datasource URL is `jdbc:postgresql:///dottracker?cloudSqlInstance=<project>:us-central1:<instance>&socketFactory=com.google.cloud.sql.postgres.SocketFactory`.
- Secret Manager holds the database password and the Google client secret. Cloud Run gets `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` and `DOT_TRACKER_ADMIN_EMAILS` as environment variables, with the secrets referenced from Secret Manager.
- The Google OAuth client lives in the same project. Until a domain is chosen, the app is reached at the `run.app` address Cloud Run gives the service, and the client's redirect URI uses that address. A custom domain later only changes the redirect URI.
- Cloud Run's startup and liveness probes use `/actuator/health`, the only actuator endpoint exposed. `server.forward-headers-strategy=framework` makes redirects use `https` behind Cloud Run's front end.

The bill is about $10 a month, nearly all of it Cloud SQL, at about $7.70 for the instance and $1.70 for its 10 GB minimum of SSD storage. Cloud Run, Artifact Registry, Secret Manager, logging and egress for a small group all fit in their free tiers, as long as Artifact Registry keeps only the last few images.

`docs/deploy.md` holds the one-time `gcloud` setup and the deploy steps. Deploying is `gain bootBuildImage` tagged for Artifact Registry, a `docker push` and a `gcloud run deploy` with the new image.

## Build order for the first version

The first version is built in seven phases, each finished, reviewed and passing `gain build` before the next starts.

1. Foundation. Fill in `settings.gradle.kts`, `gradle/libs.versions.toml` and `build.gradle.kts` with the stack above, and add `mise.toml`, `compose.yaml`, `.gitignore` entries for `node_modules`, `.env` and `.gain`, and the `frontend` skeleton with its placeholder page. Add `DotTrackerApplication`, `ClockConfiguration`, `RestExceptionHandler`, `SpaForwardingController`, `application.yaml`, `V1__schema.sql`, `PostgresTestConfiguration` and `ApplicationContextTest`. The jar serves the placeholder page from `/`.
2. Sign-in and membership. Build `SecurityConfiguration`, `MemberOidcUserService`, `CurrentMember`, the member and invite domains with their controllers, `/join/{code}` and Spring Session, with their tests. Add the demo profile's form login and members, and `TestDotTrackerApplication`.
3. The catalog. Curate `inventory/catalog.csv` by the rules above, write `V2__catalog.sql` and `CatalogSeedTest`, and build the book domain, with its entities, repositories, `BookService` and the controllers for books and product lines, with their tests.
4. Collections. Build the collection domain, with `CollectionEntry` and the collection rules' unit tests, `CollectionService`, `CollectionRestController` and their tests. Add `DemoSeeder`'s collections for the demo members.
5. The frontend foundation and the main screens. Add tokens and fonts from the mockups, the responsive frame with its tab bar and top bar, the router, `api.js`, the stores and the sign-in and welcome screens, then the Browse and Book screens with the page grid, ratings, notes and the desktop list-and-detail layout.
6. The remaining screens. Build Shelf, Wishlist and Account, and the admin screens, which are the book editor and the Group screen with invites and roles.
7. Deploy. Write `docs/deploy.md`, create the Google Cloud resources and the Google client, sign in as the first admin and walk through every screen on a phone and a desktop. Then she invites her friends.

## Later work

None of this is needed to start, and its order isn't set.

She asked about finding new books automatically, since she hears about new books and store exclusives from the Lifelines Facebook page and from stores. The Lifelines website doesn't offer an obvious product feed, so the work starts by working out which sources can be read reliably. Likely candidates are the Lifelines catalog pages and retailer pages such as Target's, fetched on a schedule and parsed with jsoup, and possibly a scheduled web search for new Lifelines books. A Facebook page is the hardest source, since reading one goes through Meta's API and its app review. New books and changes land in a review queue that an admin accepts or rejects, never straight into the catalog, and books gain a `cover_url` and a `source_url`.

Sharing lets everyone in the group see each other's shelves, what they're working on and how they rated each book, and every book shows the group's average rating and who owns it. There are no friend requests or profiles, since everyone in the app joined by invitation. Each member has checkboxes for whether others see their ratings and their pages, both on by default. Notes split into the private note from the first version and a public note the group can read. `member` gains the two visibility flags, `collection_entry` gains `public_notes`, and `GET /api/members/{id}/collection` and `GET /api/books/{id}/ratings` serve the screens.

Release years group the list by the year a book came out, such as 2024 and 2025 books, once the catalog grows. `book` gains a nullable `release_year` that admins fill in, and Browse can group by it.

Facebook sign-in serves anyone without a Google account. Spring Security ships Facebook as a common OAuth2 provider, but it isn't OpenID Connect, so `MemberOidcUserService` gets a sibling `OAuth2UserService` for it, and `member` gains a `facebook_subject`.

Smaller ideas for any later point are a photo of each finished page, statistics such as books finished per month, and an export of a person's own data as CSV.

## Decisions for you

- The production domain is open. The app works at its `run.app` address until then, and a domain only changes the Google redirect URI.
