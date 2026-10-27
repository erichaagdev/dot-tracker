# Dot Tracker technical plan

Dot Tracker is a web app for a small group of friends who collect Lifelines FlowArt books. It holds one shared catalog of every book, and each person keeps their own record against it: whether they own a book or want it, how far along they are, which pages they've finished and how they rated it. Everyone in the group can see everyone else's record. It runs as one Spring Boot service with a Svelte frontend and a Postgres database, works on phones and desktops, and is reachable over HTTPS so friends can use it from anywhere.

This is the plan to build from. `CLAUDE.md` at the repository root holds the conventions every change follows. `docs/plans/overview.md` is the same plan for a non-technical reader, and the mockups in `docs/mockups/index.html` show the first version's screens at phone and desktop sizes. No mockup covers People, someone's shelf or someone's copy of a book, which reuse the pieces of the Shelf and Book screens. Open the mockups in a browser before building any screen.

## Scope

The first version covers a starting catalog of 93 books, sign-in by invitation, and each person's own tracking of what they own and want, their progress, finished pages, ratings and notes. It also covers browsing one running list with search and filters, a home screen of what you're working on, and admin screens to add and edit books and invite friends.

It also covers sharing. Everyone in the group sees everyone else's collection, with what they own and want, their progress, finished pages, ratings and notes. There are no friend requests, privacy settings or hidden parts, since everyone in the app joined by invitation. Each book shows how the rest of the group is doing with it and the group's average rating, and People lists everyone with a shelf of their own to open.

Finding new books on the web isn't needed to start, since the catalog is small enough to keep by hand. "Later work" at the end describes it, along with release years and Facebook sign-in.

## The one modeling decision everything hangs on

The app keeps two kinds of data apart. The catalog is shared and describes the book itself: its product line, name, page count, where it's sold and for how much, whether it's a store exclusive and whether it can still be bought. A collection entry belongs to one person and one book, and holds that person's state: owned, wanted, progress, finished pages, ratings and notes. Nothing personal lives in the catalog, so one person's checkmarks never change anyone else's record. Every entry is open for the whole group to read, and only its owner changes it.

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
| GraalVM Native Build Tools | 1.1.17 | The `org.graalvm.buildtools.native` Gradle plugin, which makes `bootBuildImage` build a native image |
| foojay resolver | 1.0.0 | Settings plugin for the toolchain download |
| Node | 24 LTS | Node 26 is due to enter LTS at the end of October 2026, so move to it then |
| Svelte | 5.57 | Runes, plain JavaScript |
| Vite | 8.3 | With `@sveltejs/vite-plugin-svelte` 7.3 |
| Lucide | 1.61 | `@lucide/svelte`, the only frontend dependency beyond the toolchain |
| junixsocket | 2.11.1 | `com.kohlschutter.junixsocket:junixsocket-common` and `junixsocket-native-common`, connect to Cloud SQL through Cloud Run's Unix socket in production |

## Repository layout

```
build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml
.github/workflows/deploy.yml      Builds, tests and deploys every push to main
CLAUDE.md                         Conventions every change follows
compose.yaml                      Postgres 18 for local runs
docs/plans/                       This plan and the overview
docs/deploy.md                    The first deploy to Google Cloud and every redeploy
docs/mockups/index.html           Screen mockups
src/main/java/dev/erichaag/dottracker/
  DotTrackerApplication.java, DotTrackerProperties.java, DemoSeeder.java
  ClockConfiguration.java, RestExceptionHandler.java, SpaForwardingController.java, PersistenceRuntimeHints.java
  security/                       SecurityConfiguration, MemberOidcUserService, MemberOidcUser, CurrentMember, JoinController,
                                  DemoSignInController, SessionRuntimeHints
  member/                         Member, its repository, service and controller
  invite/                         Invite, its repository, service and controller
  book/                           ProductLine, LineColor, Book, Listing, Availability, their repositories, BookService,
                                  BookDeleting, BookRestController, ProductLineRestController
  collection/                     CollectionEntry, Progress, Wanted, CollectionEntryRepository, CollectionService,
                                  CollectionRestController
  people/                         PeopleService and PeopleRestController, everyone's collections for the group to read
src/main/resources/
  application.yaml, application-demo.yaml
  db/migration/                   V1__schema.sql, V2__catalog.sql, V3__invite_email.sql
src/test/java/dev/erichaag/dottracker/...
src/test/java/dev/erichaag/dottracker/TestDotTrackerApplication.java
frontend/                         Svelte app, built into the jar's static resources
  public/                         manifest.json and the app icons, copied into the build as they are
  src/lib/                        api.js, storage.js, format.js, status.js, the router and the *.svelte.js stores
  src/components/, src/screens/   Shared pieces and one component per screen
  src/styles/                     tokens.css, fonts.css and one partial per area, with the fonts in fonts/
```

## Build

The build follows the shape of `~/Projects/erichaagdev/meeting-assistant/build.gradle.kts`. The `java` plugin applies by its id, and `org.springframework.boot`, `net.ltgt.errorprone` and `org.graalvm.buildtools.native` through catalog aliases. The project's group is `dev.erichaag`, which the AOT tasks use to name the folder their native image metadata goes in. Every dependency version comes from the Spring Boot BOM, imported as a `platform`, except Error Prone, NullAway and junixsocket. Error Prone runs on every compile of the app's own code with NullAway at `ERROR`, `OnlyNullMarked` and `JSpecifyMode`. It's off for `compileAotJava` and `compileAotTestJava`, which compile the code Spring generates.

The catalog's libraries are these starters and modules.

- `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`
- `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql`
- `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-client`, `spring-boot-starter-session-jdbc`
- `org.jspecify:jspecify`
- `com.kohlschutter.junixsocket:junixsocket-common` and `junixsocket-native-common` as `runtimeOnly`, used only when the datasource URL names junixsocket's socket factory
- `spring-boot-docker-compose` as `developmentOnly`
- For tests, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql`, `testcontainers-junit-jupiter`, AssertJ, JUnit Jupiter and the platform launcher

Two `Exec` tasks build the frontend. `npmInstall` runs `npm ci` in `frontend`, and `buildFrontend` runs `npm run build` into `build/frontend/static`. `build/frontend` is added as a main resources directory, so the built app lands in the jar's `static` folder and is served from `/`. Both run npm through `sh -c`, so it's found on the PATH of the shell that started the build, where mise puts the pinned Node, rather than on whatever PATH the Gradle daemon started with. Both declare their inputs and outputs so Gradle skips them when nothing changed.

`bootBuildImage` produces the production container image with `BP_JVM_VERSION=25` set on the task, so there is no Dockerfile. With the native build tools plugin applied, it builds a GraalVM native image, as "Hosting and deployment" describes. The plugin also brings in Spring's AOT processing. `processAot` generates the application context's code and hints into the boot jar, and `processTestAot` does the same for the test contexts, which `test` depends on. Neither needs GraalVM, and `build` runs neither `nativeCompile` nor `nativeTest`, so `gain build` needs only the JDK. Run every Gradle command through `gain`, as in `gain build` or `gain bootRun`.

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
  color text not null check (color in ('DBL', 'MINI', 'PREMIUM', 'REVEAL', 'TRACE', 'POP', 'GLOW', 'STAMP')),
  version bigint not null
);

create table book (
  id uuid primary key default uuidv7(),
  product_line_id uuid not null references product_line (id),
  name text not null,
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
  book_id uuid not null references book (id),
  owned boolean not null default false,
  wanted text check (wanted in ('YES', 'MAYBE', 'NO')),
  progress text not null default 'NOT_STARTED' check (progress in ('NOT_STARTED', 'IN_PROGRESS', 'FINISHED')),
  pages_done integer[] not null default '{}',
  rating integer check (rating between 0 and 5),
  design_rating integer check (design_rating between 0 and 5),
  color_rating integer check (color_rating between 0 and 5),
  notes text,
  started_on date,
  finished_on date,
  updated_at timestamptz not null,
  version bigint not null,
  unique (member_id, book_id)
);
```

`V3__invite_email.sql` gives `invite` an `email text not null` column, the address the invite is for, which `Invite` keeps the way `Member.normalizeEmail` gives it. The migration filled in each accepted invite's email from the member who accepted it and deleted every invite nobody had accepted, since those had no address to give them. Nothing makes an email unique among invites, since an expired invite mustn't stop a new one for the same person, so `InviteService` refuses a second open invite for an email itself.

The unique index on product line and name, ignoring case, only stops the same book from being entered twice. Nothing references it, and the same name can appear in two lines, as "Animals" does in Color to Reveal and Dot by Letter Mini.

A listing is an `@Embeddable` record `Listing(String retailer, @Nullable Integer priceCents, @Nullable String url, boolean exclusive)` in an `@ElementCollection(fetch = EAGER)` with `@CollectionTable(name = "book_listing")` and `@OrderColumn(name = "position")`. Retailers are free text, and the book editor suggests the names already in use. A book is a store exclusive when any of its listings is exclusive.

`Book` holds its product line as a plain `productLineId`, the way `Invite` holds member ids, and the frontend matches it to the product lines it loads. `Book` keeps its name trimmed and never blank and a page count above 0. `Listing` keeps its retailer trimmed and never blank, its price never negative, and its link either none, for a blank one, or a URI with an `http` or `https` scheme and a host, refusing anything else with "A store link has to start with http:// or https://". `ProductLine` keeps its name trimmed and never blank and a default page count above 0. A product line's `color` is the `LineColor` family its covers are drawn in, stored by its name like the other enums. Related lines share a family, such as `TRACE` for every Trace line and `PREMIUM` for the Large Dot, Metallic and Premium lines, and the color never depends on the line's name, so fixing a name leaves the color alone. A new book gets its line's `default_page_count` unless the admin gives it its own, and changing a line's default leaves its books' counts as they are.

`pages_done` maps to a `List<Integer>` field, which Hibernate 7.4 stores as a Postgres array with no annotation, since it maps a `List` of a basic type to the dialect's array type. `rating` is the overall score for how much someone liked the book, and it's the one lists and the shelf show. `design_rating` and `color_rating` are optional extras on the book screen. A null rating means not rated yet, and 0 is a real score. The ratings are `integer` columns mapped to `Integer` fields, since `ddl-auto=validate` refuses an `Integer` field over a `smallint` column.

`CollectionEntry` holds `member_id` and `book_id` as plain `memberId` and `bookId` fields, the way `Book` holds its product line. A collection entry's `book_id` has no `on delete cascade`, so the database refuses to delete a book that someone tracks, and `member_id` cascades, so a member's entries go with them.

## Collection rules

`CollectionEntry` holds these rules in its own methods, which take the book's page count where a rule needs it and the injected `Clock` for `updated_at` and for today's date, `LocalDate.now(clock)`, in the group's time zone. `CollectionService` creates an entry the first time a person changes anything about a book, after checking the book exists, and a first change that's refused leaves no entry behind.

- Marking a book in progress or finished also marks it owned. Unmarking owned leaves progress alone, so a finished book someone gave away keeps its history.
- Marking a page done on a book that hasn't been started moves it to in progress and sets `started_on`.
- Marking the last remaining page done moves it to finished and sets `finished_on`. Unmarking a page on a finished book moves it back to in progress and clears `finished_on`.
- Setting progress by hand never changes pages, so someone can finish a book without ticking every page. Moving to in progress sets `started_on` unless it's already set and clears `finished_on`, moving to finished sets `finished_on` and leaves `started_on` as it is, and moving back to not started clears both.
- Pages are kept sorted and once each. A page number must be between 1 and the book's page count, for marking and unmarking alike. If an admin later lowers a page count, pages beyond it stay stored but are left out of responses and counts, so the last remaining page is the last one within the new count.
- Ratings are 0 to 5 or null. `wanted` is independent of `owned`, and the wishlist is every book marked wanted yes or maybe that isn't owned.
- Notes are trimmed, and blank notes are stored as none. Everyone in the group can read them, like the rest of the entry.
- A change that leaves the entry as it was, such as marking a page that's already done, changes nothing, not even `updated_at`, so Hibernate writes nothing and `@Version` stays put.

## REST API

Every route lives under `/api`, uses plural resources and kebab-case segments, and answers JSON. Ids in paths and bodies are UUID strings. Instants are ISO 8601 strings and dates are ISO dates. A request without a session gets a 401, never a redirect, so the frontend can show the sign-in screen. The one route open without a session is `GET /api/invites/pending`, which the welcome screen reads before anyone signs in. A member on an admin route gets a 403 `ProblemDetail` whose `detail` is "Only an admin can do that.", from `RestExceptionHandler` handling Spring Security's `AccessDeniedException`.

| Method and path | Who | What it does |
|---|---|---|
| `GET /api/me` | member | The signed-in member, with their id, email, display name, role and when they joined |
| `POST /api/logout` | member | Ends the session |
| `GET /api/books` | member | The whole catalog with listings, by name. About a hundred rows, so the frontend filters and searches locally |
| `GET /api/books/{id}` | member | One book |
| `POST /api/books` | admin | Adds a book and answers 201 |
| `PUT /api/books/{id}` | admin | Edits a book |
| `DELETE /api/books/{id}` | admin | Removes a book added by mistake with its listings, and answers 204 even when it's already gone. A book anyone has a collection entry for is refused with a 400 that says to mark it retired instead |
| `GET /api/product-lines` | member | Product lines in sort order |
| `POST /api/product-lines`, `PUT /api/product-lines/{id}` | admin | Adds a product line and answers 201, or edits one |
| `GET /api/collection` | member | Every entry of the signed-in member |
| `PUT /api/collection/{bookId}/owned` | member | Body `{"owned": true}` |
| `PUT /api/collection/{bookId}/wanted` | member | Body `{"wanted": "MAYBE"}`, or null to clear |
| `PUT /api/collection/{bookId}/progress` | member | Body `{"progress": "IN_PROGRESS"}` |
| `PUT /api/collection/{bookId}/pages/{page}` | member | Marks a page done |
| `DELETE /api/collection/{bookId}/pages/{page}` | member | Marks a page not done |
| `PUT /api/collection/{bookId}/ratings` | member | Body `{"overall": 4, "design": 5, "color": null}`, which sets all three, so a null or missing rating is cleared |
| `PUT /api/collection/{bookId}/notes` | member | Body `{"notes": "..."}` |
| `GET /api/people` | member | Everyone in the group by name, each with how many books they own, are working on and have finished |
| `GET /api/people/{id}/collection` | member | Every entry of one member, in the same shape as `GET /api/collection`, or a 404 for someone who isn't in the group |
| `GET /api/books/{id}/entries` | member | Everyone's entry for one book by name, with the group's average overall rating, or a 404 for a book that's gone |
| `GET /api/members` | admin | Everyone in the group |
| `PUT /api/members/{id}/role` | admin | Body `{"role": "ADMIN"}` or `"MEMBER"`. Refused for the group's last admin |
| `GET /api/invites`, `POST /api/invites` | admin | Lists open invites newest first, or creates one from a body `{"email": "kim@example.com"}` and answers 201. Each invite has its `id`, the `email` it's for, its full `/join/<code>` `link` built from the address the admin used, `createdBy` and `createdByName` for the admin who made it, and when it was made and expires. Creating one is refused with a 400 whose sentence names the problem, for something that isn't an email address, an email that belongs to a member or an email that already has an open invite |
| `DELETE /api/invites/{id}` | admin | Revokes an invite, and answers 204 even when it's already gone |
| `GET /api/invites/pending` | anyone | `{"email": ..., "expiresAt": ...}` for the open invite held in the session, or a 404 saying the invite has expired or was already used |
| `GET /join/{code}` | anyone | Starts sign-in with an invite, outside `/api` |

Every collection write answers with the updated entry, and every write is idempotent, so a retried request does no harm. Marking a page that's already done, or unmarking one that isn't, changes nothing and still answers 200. Every collection write acts on the signed-in member, from `CurrentMember`. Admin routes carry `@PreAuthorize("hasRole('ADMIN')")` with `@EnableMethodSecurity`.

A book comes back as `id`, `productLineId`, `name`, `availability`, `pageCount`, `listings` and `exclusive`, where each listing has `retailer`, `priceCents`, `url` and `exclusive`, and the book's `exclusive` is true when any listing is. `POST` and `PUT /api/books` take the same fields except `id` and `exclusive`, and a missing `pageCount` means the product line's default. A product line comes back as `id`, `name`, `defaultPageCount`, `sortOrder` and `color`, and its `POST` and `PUT` take all of them but `id`. `BookService` refuses a second book with the same name in a product line, or a second product line with the same name, ignoring case, with a plain sentence before the unique indexes would. `GET /api/books` loads every book with its listings in one query through an `@EntityGraph` on the repository method, since an eager element collection otherwise costs a query per book.

A collection entry comes back as `bookId`, `owned`, `wanted`, `progress`, `pagesDone`, `ratings`, `notes`, `startedOn`, `finishedOn` and `updatedAt`, where `ratings` has `overall`, `design` and `color`, the same shape the ratings route takes. `wanted`, each rating, `notes` and the dates are null when unset, and `pagesDone` is in order and leaves out pages beyond the book's page count. The frontend matches an entry to its book by `bookId`. A book with no entry is one the member hasn't touched. `CollectionService` answers each change with the entry and its book's page count, and `GET /api/collection` loads the catalog once for the page counts rather than a book per entry.

The people routes are read-only and open to every member. A person comes back as `id`, `displayName`, `owned`, `inProgress` and `finished`. `GET /api/books/{id}/entries` answers `averageRating`, `ratingCount` and `entries`, where the average is over every member's overall rating, the signed-in member's included, and is null when nobody has rated the book, and each entry has `memberId`, `displayName` and `entry` in the collection entry's shape. The signed-in member's own entry is in the list too, and the frontend leaves it out of the group section. Display names are all these routes say about a member, since email and role stay on the admin-only `GET /api/members`. `PeopleService` in the `people` package reads through `MemberService` and `CollectionService`, and nothing depends on it, so the packages stay free of cycles. Each route loads in a fixed number of queries however many members and books there are: the people list in two, a member's collection in three and a book's entries in three.

The collection domain depends on `BookService` for page counts, so the book domain never calls the collection domain back. Instead, `BookService.delete` publishes a `BookDeleting` event through Spring's `ApplicationEventPublisher` before it deletes, and an `@EventListener` in `CollectionService` throws an `IllegalArgumentException` when anyone has an entry for that book. The listener runs synchronously inside the delete's transaction, so its refusal rolls the delete back and `RestExceptionHandler` answers 400. The foreign key without a cascade backs the refusal up in the database.

## Sign-in and membership

People sign in with Google through `oauth2Login`, and nobody gets in without an invitation. A closed group of friends doesn't need passwords, password resets or email sending. Not everyone in the group may have a Google account, so "Later work" covers Facebook as a second way in. The consent screen asks only for `openid`, `email` and `profile`, which lets the Google client be published to production without app verification.

1. An admin creates an invite in the app for a friend's email address and texts its link, `https://<host>/join/<code>`, to that friend. A code is 16 random bytes from `SecureRandom`, encoded as URL-safe Base64, works once, only for that email, and expires 14 days after it was made by the injected `Clock`. `Invite` keeps its email trimmed and in lowercase and refuses one that isn't an email address, and `InviteService` refuses an email that belongs to a member or already has an open invite, each with a plain sentence that names the email.
2. `GET /join/{code}`, in `security/JoinController`, checks the code, keeps it in the session and redirects to the frontend's `/welcome` screen. An expired, used or unknown code redirects to `/?signin=invite-expired` instead. The welcome screen, from the mockups' Joining screen, reads who the invite is for and when it expires from `GET /api/invites/pending`, which `JoinController` also serves, and its "Continue with Google" button links to `/oauth2/authorization/google`. The authorization request carries `prompt=select_account`, added through the customizer of a `DefaultOAuth2AuthorizationRequestResolver` in `SecurityConfiguration`, so Google always shows its account chooser and someone with more than one Google account picks the one the invite is for.
3. `MemberOidcUserService` extends `OidcUserService`, and `oauth2Login` finds it as the context's OIDC user service bean. After Google answers, it hands the account to `MemberService.signInWithGoogle` in one transaction, which answers with a `GoogleSignIn` that's either `SignedIn` with the member, `NotInvited` or `WrongAccount`. That finds the member by Google subject, then by email, which links the Google account to them, and otherwise accepts the invite held in the session when it was sent to Google's verified email, and creates the member, with the display name from Google's `name` claim, or the email when it's missing. An email listed in `dot-tracker.admin-emails` becomes an admin on first sign-in without an invite, which is how the first account exists. Matching by email, the admin bootstrap and accepting an invite all need Google's `email_verified` claim, and a member already linked to one Google account is never linked to another.
4. Anyone else is refused with an `OAuth2AuthenticationException`, and `GoogleSignInFailureHandler` sends the refusal to the sign-in screen by its error code. An open invite sent to a different email than the Google account's is `wrong_account`, which lands on `/?signin=wrong-account`, where the sign-in screen names the email the invite is for and asks them to continue with that account. The invite stays in the session after that refusal, so continuing with Google again can use the right account. Every other refusal is `not_invited` and lands on `/?signin=not-invited`, where the sign-in screen asks them for an invite link, and every other failed Google sign-in lands there too.

`Invite` keeps `created_by` and `accepted_by` as plain member ids rather than entity references. `MemberService` depends on `InviteService` to sign people in with an invite, so `InviteService` reads members through `MemberRepository` instead of `MemberService`, which would make the two services depend on each other. It reads them to refuse an invite for someone already in the group and to name the admins who made the open invites, which loads in two queries however many there are. `MemberService` refuses to make the group's last admin a member, with a plain sentence, and locks the admin rows while it checks, so two admins demoting each other at once can't leave the group without one. A session holds the role its member signed in with, so a change of role deletes that member's sessions through `FindByIndexNameSessionRepository.findByPrincipalName` with their id. A demoted admin loses admin rights at once, and a promoted member signs in again to get them.

The signed-in user is named after their member id under either sign-in. `MemberOidcUserService` answers with a `MemberOidcUser`, a `DefaultOidcUser` whose name is the member id and whose only authority is `ROLE_ADMIN` or `ROLE_MEMBER`. The demo profile's `UserDetailsService` finds a member by the email typed into the form and answers with a user named after their id. `CurrentMember` reads the id from the security context's authentication name, so controllers work the same under both. The request cache is off, since only `/api` calls need a session and none of them is a page to come back to, so signing in always lands on `/`.

Sessions live in Postgres through Spring Session JDBC, last 90 days and survive restarts and deploys. The session cookie is `Secure`, `HttpOnly` and `SameSite=Lax`, with `server.servlet.session.cookie.max-age` set so a phone keeps it after the browser closes. The demo profile turns `Secure` off, since it runs on plain `http://localhost`, where some browsers won't keep a `Secure` cookie.

Google's client id and secret come from the `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` environment variables, which `application.yaml` names as placeholders in `spring.security.oauth2.client.registration.google`, and the admin emails from `DOT_TRACKER_ADMIN_EMAILS` the same way. Locally, `spring.config.import=optional:file:.env[.properties]` reads them from a git-ignored `.env` in the same `NAME=value` form. Spring Boot leaves a placeholder it can't resolve as written, so without them the Google registration still exists, with a client that can't sign anyone in, and tests, `ApplicationContextTest` and the demo profile all start without Google credentials.

CSRF protection uses `http.csrf(csrf -> csrf.spa())`, which `CsrfConfigurer` has in Spring Security 7.1.2. It sets an `XSRF-TOKEN` cookie on every response that doesn't already carry one, which `api.js` reads and sends back as `X-XSRF-TOKEN` on every write. `POST /api/logout` ends the session and answers 204. Behind Cloud Run's front end, `server.forward-headers-strategy=framework` makes the OAuth redirect URI use `https`.

## Seeding the catalog

`V2__catalog.sql` holds the starting catalog of 93 books across 16 product lines, curated once from a spreadsheet found online, with its spelling fixed and duplicate rows merged. Every later catalog change goes through the app. The seed holds no one's collection, so everyone starts with an empty shelf.

## Frontend

The frontend is one Svelte 5 app in plain JavaScript, built by Vite, and served by Spring Boot from the same origin, so cookies and CSRF need no cross-origin setup.

- Screens are Shelf, Browse, Book, People, a person's shelf, a person's copy of a book, Wishlist, Account, Book editor, Group, Sign in, Welcome, which an invite link lands on at `/welcome`, and Privacy at `/privacy`, which anyone can open signed in or not and which the Google client names as the app's privacy policy. The mockups show Shelf, Browse, Book, the book editor and the invite's welcome screen at phone width, and Browse with a book open at desktop width. Wishlist reuses Browse's rows, and Account and Group are short lists in the same style. The book editor and Group are for admins, and a member who opens either one's link sees a plain message saying an admin looks after that.
- Below 900 px wide the app is one column with a bottom tab bar of Shelf, Browse, People and Wishlist, and a book opens as its own screen. The member's initials at the top right open Account. They're the one control without a word on it, so the link is named "Your account", and it shows as the current page on Account and Group. From 900 px a top bar replaces the tab bar. It holds Shelf, Browse, People and Wishlist, then "Add a book" and "Group" for admins, then Account beside the member's initials. Its labels never wrap, and below 1100 px, where an admin's bar runs out of room, "Add a book" and "Group" leave it, since Account links to both at every width. Browse puts the filters on the left and the list beside them, and opening a book adds its detail as a third column on the right, so a desktop user never leaves the list. The detail column is 372 px wide and narrows to 320 px on smaller windows. Each column scrolls on its own. At that width `/books/<id>` is Browse with that book open, and clicking a row goes there, so the open book is a link like any other. Close at the top of the detail column, or Escape, goes to `/browse` with the filters as they were, and the list takes the whole width again. `src/lib/viewport.svelte.js` tells screens which layout they're in, at the same 900 px the stylesheets use.
- `src/lib/router.svelte.js` keeps routes in the path, which are `/` for Shelf, `/browse`, `/books/<id>`, `/books/new`, `/books/<id>/edit`, `/people`, `/people/<id>`, `/people/<id>/books/<bookId>`, `/wishlist`, `/account`, `/group`, `/welcome` and `/privacy`, so a link to a book can be shared. In-app links are plain `<a href>` elements that the router follows without a page load, and going back puts the earlier screen's scroll position back. `SpaForwardingController` forwards any `GET` without a file extension outside `/api`, `/oauth2`, `/login`, `/join` and `/actuator` to `index.html`.
- `src/lib/api.js` reads the `XSRF-TOKEN` cookie into the `X-XSRF-TOKEN` header on every write, turns a refused request's `ProblemDetail.detail` into the message shown, with a plain fallback when there's none or the network is down, and sends a 401 to the sign-in screen.
- The sign-in screen shows whenever `/api/me` answers 401. Its "Continue with Google" links to `/oauth2/authorization/google`, and it explains `?signin=not-invited`, `?signin=wrong-account`, `?signin=invite-expired` and `?signin=role-changed` in plain words. For `?signin=wrong-account` it reads `/api/invites/pending` to name the email the invite is for, and says it more generally when that answers 404. The welcome screen and the privacy page never ask `/api/me`, and the sign-in screen, the welcome screen and Account link to the privacy page. The welcome screen reads `/api/invites/pending` and says who the invite is for, such as "This invite is for kim@example.com. Continue with the Google account for that address.", with "Continue with Google" and when the invite expires, or the 404's message when the invite has expired or was used.
- `src/lib/me.svelte.js` holds the signed-in member and signs out. `src/lib/catalog.svelte.js` loads the books and product lines once, and adds, changes and removes books for the editor. `src/lib/collection.svelte.js` loads the member's entries and owns every change to them. `src/lib/group.svelte.js` holds the members and open invites for the Group screen. `src/lib/people.svelte.js` holds everyone in the group, each person's entries and everyone's entries for a book, fetched fresh whenever a screen that shows them opens. `src/lib/browse.svelte.js` holds Browse's search and filters, `src/lib/theme.svelte.js` the theme picked on Account, and `src/lib/notice.svelte.js` the message that pops up after a refused change.
- Changes apply on screen at once, worked out with the same collection rules the backend follows, and then send their request. Requests for the same book go one after another, so quick taps on the page grid never race each other's `@Version`. Each answer becomes the saved entry. A refused request drops its change, so the entry goes back to what the backend last saved plus any changes still waiting, and the refusal's message shows.
- Browse is one list of every book, grouped by product line in sort order and A to Z within each. Search matches names, ignoring case, accents and apostrophes. The show filters are All, Own, Want, In progress, Finished and Don't own, where Want is the wishlist, and they're chips on a phone and a list with counts on a desktop, beside a product line filter. Filters and search are remembered per device through `src/lib/storage.js`.
- Each Browse row has its line's cover color, the book's name, its status pills and an Own box. The pills are pages done as "x of y", finished, not started for a book that's owned, want it, coming soon, no longer sold and a store exclusive such as "Target exclusive". The desktop table puts the store, or the exclusive, under the name and the pills with the overall rating in a status column. A container query on the list column folds the status under the name too when the column is narrow, as beside an open book on a smaller window, so names wrap between words at worst.
- The book screen shows the name, line and page count, the store exclusive, availability and want-it pills, an Own box with the started and finished dates, a want-it choice of yes, maybe or no, the progress choice, the page grid, the ratings, where to buy it with each store's price and the notes, which save when the box loses focus. A short line under the notes label says everyone in the group can read them. Tapping a picked rating or want-it answer again clears it.
- Below your own copy, on the phone's book screen and in the desktop detail column, "Your group" shows the rest of the group's copies. It counts how many others are working on the book and how many finished it, names them, and shows the group's average overall rating and how many people it's from. Each person is a row with their initials, their progress, such as "12 of 20 pages" or "Finished", whether they own it or want it, and their overall rating, and the row opens their copy. Your own copy isn't repeated there. An entry with nothing to show, such as one left after unchecking Own or one that only answers no to want-it, counts as no entry, and when nobody else has one a short line says so. Changing your own overall rating fetches the group again once it's saved, so the average includes it.
- Lists, the shelf and the desktop table show only the overall rating. The book screen shows it first, with the design and color ratings below it.
- Shelf shows how many books the member owns out of the whole catalog, how many they've finished and how many are on their wishlist. "Working on" has a card for each book in progress, longest running first so the cards stay put, with its line's cover, its pages as "x of y" and a dot for each page. "Recently finished" lists the five most recently finished books with their overall rating. Every card and row opens its book, and an empty section says how to fill it.
- People at `/people` lists everyone in the group by name, each with their initials and pills for how many books they own, have in progress and have finished, and marks you with "You". Your row opens your own Shelf and anyone else's opens their shelf at `/people/<id>`.
- A person's shelf is the Shelf's layout with their name, through the same `ShelfView` component. It has their counts, what they're working on with a dot for each page, every book they've finished with its rating, everything they own grouped by product line and their wishlist grouped the same way. Every book opens their copy of it.
- A person's copy of a book at `/people/<id>/books/<bookId>` is `BookDetail` in its read-only mode, given the person and their entry. A banner says whose copy it is and that only they can change it, with a link to their shelf. Their progress and whether they own or want it are pills beside their dates, `PageGrid` shows their pages as small flat dots that aren't buttons, `RatingChoice` shows each rating as words, and their notes are plain text. A link at the bottom opens your own copy. Your own id at either route goes to your Shelf or your copy instead. From 900 px it's a centered column rather than part of Browse.
- Wishlist is every book marked want yes or maybe that isn't owned, grouped by product line in Browse's rows on a phone and its table rows on a desktop.
- Account shows the member's name, email and role, a theme choice of Device, Light or Dark, and signs out. Device follows the system's light or dark setting, and the choice is remembered per device through `src/lib/storage.js`. For an admin Account also links to "Add a book" and "Group", since a phone has no top bar and a smaller desktop window has no room for them in it.
- The book editor at `/books/new` and `/books/<id>/edit` has the name, the product line, the pages, which a new book takes from its line until the admin types a count, whether it can be bought as Available, Coming soon, Retired or Not sure, and a card for each store with its name, a price in dollars, an optional link and "Only sold here". The store field suggests the store names the catalog already uses, a link without `http://` or `https://` gets `https://`, and the book screen only links a store whose link starts with one of them. The editor checks for a name, a whole number of pages and prices in dollars before it sends anything, and drops a store left completely blank. On a phone, "Save changes" sits in an action bar at the bottom in place of the tab bar. An existing book can be deleted from the editor, which asks first, the one place the app does, and shows the backend's refusal when someone tracks the book. The book screen and the desktop detail column link admins to the editor, and saving a lowered or raised page count fetches the member's entries again, since the backend leaves out pages beyond the count.
- Group lets an admin invite a friend by typing their email under a visible label, which shows the new link with "Copy link" and, where the browser has `navigator.share`, "Share". A refused invite, such as one for someone already in the group, shows the backend's sentence under the email field. Every admin sees every open invite, each with the email it's for, who made it, shown as "you" for the admin's own, and when it was made and expires, with the same buttons and "Revoke". The members are listed with a Member or Admin choice each. Picking the other choice asks first, in that member's row the way the book editor asks before deleting a book. The question uses the person's name, says what the role allows and that they'll be signed out, or that you will be for your own role, and its button names the change, such as "Make Bea an admin", beside "Cancel". Revoking and a confirmed role change show at once and put things back with the backend's refusal, such as for demoting the last admin. Changing your own role ends your sessions on the backend, so the app goes straight to the sign-in screen with `?signin=role-changed` rather than waiting for the next request's 401.
- The mockups' `:root` block is the starting token set for `src/styles/tokens.css`, light and dark. The dark theme applies when the system asks for it unless `<html>` has `data-theme="light"`, and always with `data-theme="dark"`, which is how the theme picked on Account takes effect. A small inline script in `index.html` sets `data-theme` from the remembered choice before the first paint, so a reload never shows the other theme first. The token set adds a `--l-<family>` color for each product line color family, such as `--l-trace` for `TRACE`, and a line's covers and swatches take the token its `color` names. Fonts are Bricolage Grotesque for headings and Atkinson Hyperlegible Next for everything else, vendored in `src/styles/fonts/` as `woff2` files from Google Fonts with their OFL license files, rather than loaded from Google. The favicon is the brand mark's three dots.
- A web app manifest at `frontend/public/manifest.json` lets people add the app to their home screen, where it opens without browser chrome. It's named Dot Tracker, starts at `/` in `standalone` display and takes `--surface-2` as its theme and background color, and `index.html` sets `theme-color` to `--surface-2` for the light and dark system settings. A picked theme puts its own `theme-color` ahead of those two, so the browser's bar matches it. The brand mark is a spiral pad like the books, whose page is a grid of nine dots with six colored in, one in each of six line families' colors, and three left pale. `Brand.svelte` draws it inline, colored from tokens so it follows the theme, and `src/brand.svg` is the favicon, with lighter edges and coils in a dark browser. The icons are the pad tilted a little on a light background, as 192 and 512 px PNGs and a 180 px `apple-touch-icon.png`, rendered once with headless Chrome. The 512 px icon is also listed as maskable, since the pad sits inside the safe zone. The file is `manifest.json` rather than `manifest.webmanifest` because Spring Boot has no media type for `.webmanifest` and serves `.json` as `application/json`. There's no service worker, since the app needs the network anyway.

## Local development

Docker Desktop has to be running for local runs and for tests. `compose.yaml` defines a `postgres:18` service with a named volume, and `spring-boot-docker-compose` starts it on `gain bootRun`, so the app runs on port 8080 against a database that persists between runs. Running it this way needs a Google OAuth client with `http://localhost:8080/login/oauth2/code/google` as a redirect URI, and a git-ignored `.env` at the root with `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` and your email as `DOT_TRACKER_ADMIN_EMAILS`, one `NAME=value` per line without quotes.

`gain bootTestRun` starts `TestDotTrackerApplication`, which runs the app through `SpringApplication.from(...).with(...)` with a throwaway Testcontainers Postgres and the `demo` profile on port 8889. The demo profile, in `application-demo.yaml` and the `@Profile("demo")` beans, swaps Google sign-in for Spring Security's form login and its default page at `/login`, which `DemoSignInController` redirects `/oauth2/authorization/google` to, so the sign-in screen's button works the same in the demo. It has three members, `ana@example.com` as an admin and `bea@example.com` and `cam@example.com` as members, all with the password `demo`. Its `DemoSeeder` saves the three members through the repository when there are no members yet, and gives each of them a collection when there are no collection entries yet, so every screen has something on it. Ana's collection is the one the mockups show. Cabin Getaway is in progress at pages 1 to 15 with a note, started 27 days before the run, Corner Cafe is in progress at 9 of 20 pages, the Mini Cottage Garden is in progress, eight finished books carry ratings with Gingerbread Delight and Home For The Holidays finished most recently, Cozy Companions is owned but not started, and five books are on her wishlist. Bea's and Cam's are smaller and mostly different books, but both share Cabin Getaway and Corner Cafe with Ana, each at a different point. Bea finished Cabin Getaway with a 5 and a note and is partway through Corner Cafe with a note, and Cam is 12 pages into Cabin Getaway with a note and finished Corner Cafe with a 3, so each book's group section has people in it with ratings and notes. The seeder builds every entry through `CollectionEntry`'s own methods with clocks set back a number of days, so the seeded dates follow the same rules as real ones and stay recent whenever the demo runs.

`npm run dev` in `frontend` serves the app with hot reload on port 5173 and proxies `/api`, `/join`, `/login`, `/oauth2` and `/logout` to port 8889 by default, or to whatever `DOT_TRACKER_API_URL` names. Gradle's toolchain provides Java 25, and a `mise.toml` at the root pins Node 24 for mise.

## Testing

- Entity rules, such as the collection rules above, get plain unit tests.
- `CollectionServiceTest` imports `BookService` beside `CollectionService`, so it checks that pages round-trip through the `integer[]` column in order, that a page already done writes nothing, that a lowered page count leaves later pages stored but out of the count, and that deleting a book someone tracks is refused.
- Repositories and services use `@DataJpaTest` with `@AutoConfigureTestDatabase(replace = NONE)` and `@Import(PostgresTestConfiguration.class)`, whose `@ServiceConnection` bean starts one `postgres:18` container that every test reuses. The real Flyway migrations run, so a test sees the seeded catalog. The service under test is added with `@Import`.
- Every controller gets a `@WebMvcTest` with MockMvc, the service as a `@MockitoBean` and the real `SecurityConfiguration` imported. Those tests pin JSON field names, status codes, the 401 for a missing session, the 403 for a member on an admin route and the CSRF requirement on writes, using `oidcLogin()` and `csrf()` from `spring-security-test`. `SecurityConfigurationTest` covers the rules that belong to no controller, such as signing out, the not-invited redirect, the `prompt=select_account` that asks Google for its account chooser and the demo form login, and sends the CSRF token the way the frontend does, from the `XSRF-TOKEN` cookie into the `X-XSRF-TOKEN` header, since `csrf()` swaps the context's token repository for one that sets no cookie. `DemoSignInControllerTest` checks that the demo sends "Continue with Google" to its form login.
- `MemberOidcUserServiceTest` covers each sign-in path, being an existing member by Google subject, an existing member by email whose Google account gets linked, accepting a valid invite, refusing an expired or used one, refusing an invite sent to another email as the wrong account while keeping it in the session for the right one, bootstrapping an admin, refusing an unverified email and refusing a stranger. `GoogleSignInFailureHandlerTest` checks which sign-in screen message each refusal lands on.
- `InviteEmailMigrationTest` migrates a schema of its own to V2, adds an accepted and an unaccepted invite, and checks that V3 gives the accepted one its member's email and deletes the other.
- `MemberServiceTest` imports Spring Session's `JdbcHttpSessionConfiguration`, the configuration Spring Boot imports for the app, and checks that a change of role deletes that member's sessions in Postgres and leaves everyone else's.
- `CatalogSeedTest` is a sanity check on the migrated catalog, with its 93 books, its 16 product lines in sort order, each line's color family, 25 pages for every Large Dot book and 20 for every other, and Corner Cafe, Cozy Companions and Cabin Getaway marked as Target exclusives.
- `BookServiceTest` turns on Hibernate's statistics to check that the whole catalog loads in one query, and `PeopleServiceTest` does the same to pin the people list at two queries and a member's collection and a book's entries at three each, with several members tracking several books.
- `PeopleRestControllerTest` checks that the people routes carry display names and never an email or a role.
- `ApplicationContextTest` starts the whole app once against the test container.
- `SessionRuntimeHintsTest` and `PersistenceRuntimeHintsTest` check that the types the native image needs hints for are registered.
- The tests run on the JVM on every build and never as native code through `nativeTest`. The native image is checked by building it and running the container against Postgres, as "Checking a native image locally" in `docs/deploy.md` describes.

## Hosting and deployment

The app runs on Google Cloud, in one project in `us-central1`.

- Cloud Run runs the container image that `bootBuildImage` builds, pulled from Artifact Registry, whose cleanup policy keeps the five newest images. The task sets `imagePlatform = "linux/amd64"`, since Cloud Run runs amd64 and a Mac builds arm64 by default. The service gets 1 GiB of memory, startup CPU boost and between 0 and 2 instances. Cloud Run reports the native image using about 225 MiB of it, so 512 MiB would also fit. Scaling to zero keeps it free, at the cost of a short wait on the first visit after a quiet spell while an instance starts, and a minimum of 1 instance removes that wait for roughly $13 more a month. Sessions live in Postgres, so any instance serves any request.
- The image is a GraalVM native image, compiled to machine code when it's built, so it starts without a JVM to load and warm up. The `org.graalvm.buildtools.native` plugin makes `bootBuildImage` hand the boot jar to the Paketo native image buildpack, which compiles it with BellSoft Liberica NIK 25. Spring's AOT processing settles the application context at build time, so profiles and conditions are fixed then. The image always runs without the demo profile, and a property set at startup can change a value but not which beans exist. A native image reaches a class through reflection or serialization only when a hint names it. Spring and the libraries' own metadata cover most of the app. `SessionRuntimeHints` registers what the session holds that Spring leaves out, which is Google's authorization request and the signed-in member with their ID token, and `PersistenceRuntimeHints` covers a UUID array Hibernate creates and a method Flyway calls when it can't connect. junixsocket brings its own native image support. The image is built for size, because a native image starts by reading its own executable, which Cloud Run appears to read in lazily, so a smaller one starts faster. `bootBuildImage` passes `-Os`, which optimizes for size, and GraalVM's experimental relative code pointers, which shrink the table the loader fixes up at every start, and of the JNI libraries junixsocket bundles for many platforms, it keeps only the one junixsocket loads on the image's platform. `--enable-native-access=ALL-UNNAMED` lets junixsocket load it without Java's warning about restricted methods, and `BP_RUNTIME_CERT_BINDING_DISABLED` drops the buildpack's certificate helper, since nothing binds certificates to the container. On an Apple silicon Mac with 18 cores, building the arm64 image takes about 3 minutes and peaks at about 14 GB of memory, since the compiler sizes its heap to the machine it runs on. The amd64 build takes about 5.5 minutes on the 4-core, 16 GB runner GitHub gives a public repository. On the 2-core, 8 GB runner a private repository gets on GitHub's Free plan, it ran short of memory and didn't finish within an hour, which is why the repository is public. Run natively on arm64 on that Mac with one CPU, 1 GiB and the migrations already applied, the app started in about 0.2 seconds, and about 0.3 seconds passed from `docker run` to its first answer. The container used about 110 MiB once it was up. On Cloud Run, with one CPU and startup CPU boost, a cold start takes about 3 to 4.5 seconds from the instance starting to its first answer, against about 21 seconds for the JVM build. About 0.3 to 0.6 seconds of that is Cloud Run starting the container, 0.3 to 1.3 seconds is the native process starting before Spring logs anything, and 1.7 to 2.4 seconds is Spring's own startup with its first Cloud SQL connection. The arm64 executable is about 162 MB. The amd64 one is about 170 MB, of which about 68 MB is code and 89 MB is the image heap, and it goes into a layer of about 58 MB compressed that changes with every build, so each push uploads that much.
- Cloud SQL for PostgreSQL 18 holds the data, on the smallest shared-core tier with automated daily backups. It's created with `--edition=enterprise --tier=db-f1-micro`, because new Postgres instances otherwise default to the much pricier Enterprise Plus edition.
- The app reaches Cloud SQL through Cloud Run's built-in Cloud SQL connection, which handles TLS and authorizes through IAM, so the instance keeps its public IP with no authorized networks. `--add-cloudsql-instances` gives the service a Unix socket to the instance under `/cloudsql`, and the Postgres driver connects through it with junixsocket's socket factory. The service runs as its own `dot-tracker-run` service account, which has `roles/cloudsql.client` on the project and access to the two secrets below, and nothing else. The datasource URL is `jdbc:postgresql:///dottracker?socketFactory=org.newsclub.net.unix.AFUNIXSocketFactory$FactoryArg&socketFactoryArg=/cloudsql/<project>:us-central1:<instance>/.s.PGSQL.5432`.
- Secret Manager holds the database password and the Google client secret. Cloud Run gets `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` and `DOT_TRACKER_ADMIN_EMAILS` as environment variables, with the secrets referenced from Secret Manager.
- The Google OAuth client lives in the same project, made by hand in the Cloud Console, since `gcloud` can't create a web client. Its audience is External and published to production, with only the `openid`, `email` and `profile` scopes. Until a domain is chosen, the app is reached at Cloud Run's deterministic address, `https://dot-tracker-<project number>.us-central1.run.app`, which is known before the first deploy, so the client's redirect URIs are set up front. They are that address and `http://localhost:8080`, each followed by `/login/oauth2/code/google`. A custom domain later only changes the redirect URI.
- Cloud Run's startup and liveness probes use `/actuator/health`, the only actuator endpoint exposed. The startup probe checks every second for up to two minutes, so a new instance takes traffic within a second of being ready. `server.forward-headers-strategy=framework` makes redirects use `https` behind Cloud Run's front end.

The bill is about $10 a month, nearly all of it Cloud SQL, at about $7.70 for the instance and $1.70 for its 10 GB minimum of SSD storage. Cloud Run, Secret Manager, logging, Artifact Registry and egress for a small group all fit in their free tiers. Artifact Registry's five kept images come to roughly 0.3 GB, since each has its own layer of about 58 MB for the native executable.

`docs/deploy.md` holds the one-time setup, run in a named `gcloud` configuration of its own so a work configuration stays untouched, and the deploy steps. Deploying is `bootBuildImage` tagged with the commit for Artifact Registry, a `docker push` and a `gcloud run deploy` with the new image, which keeps every other setting of the running service.

GitHub Actions deploys every push to `main` through `.github/workflows/deploy.yml`, which runs `./gradlew build bootBuildImage`, so every test passes before the image exists, and then pushes and deploys the image as above. The repository is public, so the job runs free on GitHub's 4-core runner, takes about 6.5 minutes and may take up to 30. The workflow signs in through workload identity federation as the `dot-tracker-deploy` service account, which can only push to the image repository, deploy revisions of the service and run them as `dot-tracker-run`. The provider accepts a token only from a run on `main` of `erichaagdev/dot-tracker`, so no key is stored and no fork or other branch can deploy. The project's id and number are repository secrets, so they stay out of the repository and are masked in the logs. A workflow run only changes the image, so the first deploy is done by hand. The same steps still run by hand from a Mac, but there the amd64 native image builds under emulation, which takes about 9 minutes, so the workflow's `workflow_dispatch` trigger, through `gh workflow run deploy.yml`, is the way to redeploy without a push.

## Build order for the first version

The first version is built in eight phases, each finished, reviewed and passing `gain build` before the next starts.

1. Foundation. Fill in `settings.gradle.kts`, `gradle/libs.versions.toml` and `build.gradle.kts` with the stack above, and add `mise.toml`, `compose.yaml`, `.gitignore` entries for `node_modules`, `.env` and `.gain`, and the `frontend` skeleton with its placeholder page. Add `DotTrackerApplication`, `ClockConfiguration`, `RestExceptionHandler`, `SpaForwardingController`, `application.yaml`, `V1__schema.sql`, `PostgresTestConfiguration` and `ApplicationContextTest`. The jar serves the placeholder page from `/`.
2. Sign-in and membership. Build `SecurityConfiguration`, `MemberOidcUserService`, `CurrentMember`, the member and invite domains with their controllers, `/join/{code}` and Spring Session, with their tests. Add the demo profile's form login and members, and `TestDotTrackerApplication`.
3. The catalog. Write `V2__catalog.sql` and `CatalogSeedTest`, and build the book domain, with its entities, repositories, `BookService` and the controllers for books and product lines, with their tests.
4. Collections. Build the collection domain, with `CollectionEntry` and the collection rules' unit tests, `CollectionService`, `CollectionRestController` and their tests. Add `DemoSeeder`'s collections for the demo members. Make `DELETE /api/books/{id}` refuse a book once anyone has an entry for it, in favor of marking it retired, since until then it deletes any book.
5. The frontend foundation and the main screens. Add tokens and fonts from the mockups, the responsive frame with its tab bar and top bar, the router, `api.js`, the stores and the sign-in and welcome screens, then the Browse and Book screens with the page grid, ratings, notes and the desktop list-and-detail layout.
6. The remaining screens. Build Shelf, Wishlist and Account, and the admin screens, which are the book editor and the Group screen with invites and roles.
7. Deploy. `docs/deploy.md` is written, and the app's owner runs it to create the Google Cloud resources and the Google client, deploy the app, sign in as the first admin and walk through every screen on a phone and a desktop, then connects the GitHub repository so every push to `main` deploys. Then she invites her friends.
8. Sharing. Add the `people` package with `PeopleService`, `PeopleRestController` and their tests, and the routes that let any member read everyone's collections. Add Cam to the demo with collections that overlap Ana's. Build People, a person's shelf, a person's copy of a book and the group section on the book screen, and put People in the tab bar and the top bar.

## Later work

None of this is needed to start, and its order isn't set.

She asked about finding new books automatically, since she hears about new books and store exclusives from the Lifelines Facebook page and from stores. The Lifelines website doesn't offer an obvious product feed, so the work starts by working out which sources can be read reliably. Likely candidates are the Lifelines catalog pages and retailer pages such as Target's, fetched on a schedule and parsed with jsoup, and possibly a scheduled web search for new Lifelines books. A Facebook page is the hardest source, since reading one goes through Meta's API and its app review. New books and changes land in a review queue that an admin accepts or rejects, never straight into the catalog, and books gain a `cover_url` and a `source_url`.

Release years group the list by the year a book came out, such as 2024 and 2025 books, once the catalog grows. `book` gains a nullable `release_year` that admins fill in, and Browse can group by it.

Facebook sign-in serves anyone without a Google account. Spring Security ships Facebook as a common OAuth2 provider, but it isn't OpenID Connect, so `MemberOidcUserService` gets a sibling `OAuth2UserService` for it, and `member` gains a `facebook_subject`.

Smaller ideas for any later point are a photo of each finished page, statistics such as books finished per month, and an export of a person's own data as CSV.

## Decisions for you

- The production domain is open. The app works at its `run.app` address until then, and a domain only changes the Google redirect URI.
