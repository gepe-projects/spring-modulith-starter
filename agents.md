# agents.md — Project Conventions (Modular Monolith, Clean Architecture)

> This document is the single source of truth for working on this repository.
> It is written for humans **and** AI coding agents. If a rule here contradicts
> a habit you have, **the rule wins**. When in doubt, ask before deviating.

---

## 1. Project snapshot & status

| Item | Value |
|---|---|
| Type | Modular monolith (single deployable Spring Boot app) |
| Java | 25 |
| Spring Boot | 4.1.1 (modular starters, e.g. `spring-boot-starter-webmvc`) |
| Spring Modulith | 2.1.1 |
| Build | Maven wrapper |
| Base package | `com.gepe.starter` |
| Persistence | PostgreSQL + JPA + Flyway |
| Cache / secondary store | Redis |
| Scheduler | Quartz (starter present; no job example yet) |
| Verification | Spring Modulith via `ModularityTests` (see §8) |

Commands:

```bash
./mvnw test          # unit + modularity + (later) slice/integration tests
./mvnw verify        # everything, incl. packaging
./mvnw spring-boot:run   # run app locally (needs Postgres/Redis up)
```

**Current state:** a Spring Initializr starter plus the `platform` module —
started with only its `package-info.java`, it now also holds `web/` and
`logging/`. Present today: `SpringModulithStarterApplication` registered with
`@Modulith(sharedModules = "platform")`, `ModularityTests`, one context-load
test (needs the local Postgres/Redis from §5 up — it connects via the dev
defaults in `application.yaml`), and request-scoped MDC logging
(`CorrelationIdFilter` +
`logback-spring.xml`, see §7). No feature modules, no i18n beans, and no
`Dockerfile` yet. Everything below is **normative**: apply these conventions as
modules, tests, and infrastructure are implemented. Pieces that are still
planned are explicitly flagged "(planned)".

---

## 2. Module layout & the `api` / `internal` split

### 2.1 Golden rules (answers to the api/internal question)

1. **A feature module exposes only its `api` package to other modules.**
   Everything under `internal` is private to that module.
2. **`api` must NEVER depend on `internal`** — not even within the same module.
   `api` is a pure contract: Java interfaces (facades), immutable DTOs
   (`record`), and event types. It must not reference Spring Data / JPA / Web
   infrastructure types and must not import any `internal` class.
3. **`internal` MAY depend on `api` of its own module** (implementing the
   facade) and on the shared `platform` module.
4. **Another feature module may only reference `<module>.api`**, declared as
   `allowedDependencies = "<module>::API"`. Referencing another module's
   `internal`, `entity`, `service`, `repository` or `delivery` classes is a
   violation and fails the build.
5. **Dependencies point inward.** HTTP → facade (`api`) → service → repository
   → entity. Never the other way around.

Why rule 2 exists: `api` must stay import-safe for any consumer. If `api`
imported `internal`, every consumer would transitively pull in the module's
implementation — the exact leak `api`/`internal` exists to prevent.

### 2.2 Standard layout of one feature module

```
src/main/java/com/gepe/starter/<module>/
├── package-info.java          # @ApplicationModule(type = CLOSED, allowedDependencies = ...)
├── api/                       # ONLY this subtree is accessible to other modules
│   ├── package-info.java      # @NamedInterface("API")
│   ├── <Module>Api.java       # facade interface used by this module's HTTP layer AND other modules
│   ├── dto/                   # request/response records of the use-cases (inter-module contract)
│   └── event/                 # records of events this module publishes (if any)
└── internal/                  # PRIVATE: never referenced from outside this module
    ├── delivery/http/
    │   ├── req/               # HTTP request DTOs (+ jakarta.validation annotations)
    │   ├── res/               # HTTP response DTOs
    │   └── <Module>Controller.java
    ├── entity/                # JPA @Entity classes
    ├── service/               # implementation of <Module>Api + package-private helpers
    └── repository/            # Spring Data JPA repository interfaces
```

- The **module root package itself contains only `package-info.java`** — no
  classes. Otherwise those classes would sit in the module's *unnamed
  interface* and blur the boundary.
- Additional `internal` sub-packages are allowed when needed, e.g.
  `internal/listener/` for event listeners, `internal/mapper/` for
  req/res ⇄ api-dto mappers. Keep the minimum list above for every module that
  exposes HTTP.
- Package and module names: lowercase singular nouns (`user`, `order`,
  `notification`, `payment`, …). The module `id` used in annotations must match
  the package's local name.

### 2.3 Annotation templates (verified against Modulith 2.1.1)

Application class — replace `@SpringBootApplication` with `@Modulith` so the
`platform` module is registered as *shared* (usable by every module without
being listed in their `allowedDependencies`):

```java
package com.gepe.starter;

import org.springframework.modulith.Modulith;

@Modulith(sharedModules = "platform")
public class SpringModulithStarterApplication {
    // ...
}
```

Feature module — CLOSED by default; declare which other modules it may use
*and through which named interface*:

```java
// src/main/java/com/gepe/starter/user/package-info.java
@org.springframework.modulith.ApplicationModule(
        id = "user",
        allowedDependencies = {} // only shared modules (platform) are allowed
)
package com.gepe.starter.user;
```

```java
// src/main/java/com/gepe/starter/order/package-info.java
@org.springframework.modulith.ApplicationModule(
        id = "order",
        allowedDependencies = "user::API"
)
package com.gepe.starter.order;
```

Expose the contract — annotate the `api` package:

```java
// src/main/java/com/gepe/starter/user/api/package-info.java
@org.springframework.modulith.NamedInterface("API")
package com.gepe.starter.user.api;
```

Meaning of the pieces:

- `type = CLOSED` (default): other modules may only touch types inside this
  module's named interfaces (`api`); touching `internal` classes is a
  verification violation.
- `allowedDependencies = "user::API"`: only the named interface `API` of
  module `user` may be used. A bare `"user"` (no `::API`) would also be
  rejected for non-exposed types; always write the `::API` qualifier.
- Shared modules (here `platform`) are allowed **implicitly** — never list them
  in `allowedDependencies`. `@Modulith(sharedModules = "platform")` on the
  application class is what grants that implicit allowance — see §2.4.

### 2.4 The `platform` (shared) module

`platform` is shared infrastructure, declared as simply as possible — exactly
as implemented in the repository:

```java
// src/main/java/com/gepe/starter/platform/package-info.java
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.gepe.starter.platform;
```

Why `OPEN` alone is enough here:

- **Incoming:** an OPEN module does not hide internals — *all* its sub-packages
  (`config`, `web`, `exception`, `tools`, …) are accessible from every other
  module, with no `@NamedInterface` needed.
- **Cycles:** OPEN modules are excluded from Modulith cycle detection, which is
  correct for cross-cutting infrastructure.
- `platform` needs no `id` and no `allowedDependencies`. Keep it this simple —
  do not copy the CLOSED + `allowedDependencies` style of feature modules here.

Two conventions stay binding even though Modulith no longer machine-checks them
(an OPEN module's outgoing dependencies are unrestricted):

1. **`platform` must never depend on a feature module** — not even through its
   `api`. Enforced by code review; if machine enforcement is ever wanted, add
   `allowedDependencies = {}` to the annotation.
2. Feature modules reach `platform` because it is registered as *shared* on the
   application class (`@Modulith(sharedModules = "platform")`, §2.3). If that
   registration is dropped, every feature module must list `"platform"` in its
   own `allowedDependencies`.

`platform` contains cross-cutting, framework-level code only — no business
logic. Planned sub-packages:

```
platform/
├── config/          # @Configuration: Redis/cache manager, JPA auditing, scheduling, web
├── web/             # GlobalExceptionHandler (@RestControllerAdvice), CorrelationIdFilter, web config
├── exception/       # PlatformException + typed subclasses (uses error codes, see §6)
├── tools/           # ApiResponse<T>, PageResponse<T>, small framework helpers
├── i18n/            # (planned) single aggregated MessageSource + key conventions
├── persistence/     # BaseEntity (@MappedSuperclass) + auditing support
└── logging/         # MDC key constants / helpers (see §7)
```

`platform` may depend on Spring/third-party libraries and the JDK — never on
`com.gepe.starter.<feature module>`.

---

## 3. Implementation conventions (Java)

- **DTOs, commands, events, and api results are `record`s** (immutable). JPA
  entities are **not** records — use Lombok (`@Getter`, `@Setter`,
  `@NoArgsConstructor` / `@AllArgsConstructor`, `@Builder`) as appropriate.
- Loggers: Lombok `@Slf4j` or an explicit SLF4J logger. Never `System.out`.
- Bean validation lives **on HTTP request DTOs** in `internal/delivery/http/req`.
  Prefer built-in constraints; when a specific message is needed, reference a
  message key: `@NotBlank(message = "{user.validation.name.required}")`.
  (Keys are resolved through the aggregated MessageSource — see §6.)
- JPA repositories extend `JpaRepository` / `JpaSpecificationExecutor` and live
  in `internal/repository`. Entities live in `internal/entity` and extend the
  platform `BaseEntity` when audit columns are wanted.
- **Identifiers: UUID v7, generated in the application.** Every generated id is
  a **UUID v7** created with `UuidCreator.getTimeOrderedEpoch()` (dependency
  `com.github.f4b6a3:uuid-creator`) — never `UUID.randomUUID()` (v4) and never
  a DB-generated `@GeneratedValue` id. The database column is `uuid`, filled
  from the application.
  **Exception:** small, non-confidential reference/lookup tables (few rows,
  nothing sensitive) may use a plain `identity` surrogate key instead.
- **Enums live in the application only — never as a database enum type.**
  Persist an enum by its name in a text column (`@Enumerated(EnumType.STRING)`
  / a `varchar` column whose values the application or API layer validates).
  Adding a new enum constant then never requires a Flyway migration. Native DB
  enum types are forbidden.
- **Transactions** belong to the facade/service boundary
  (`@Transactional` on the implementation in `internal/service`), not on
  repositories or controllers. Cross-module calls are plain synchronous method
  calls on the other module's facade bean — no HTTP between modules.
- **Redis** is used for caching read paths: `@Cacheable`/`@CacheEvict` on
  service methods returning api DTOs (records). Cache manager, TTL, key
  serializers are configured once in `platform/config` — never per module.
- **Flyway**: one migration per schema change under
  `src/main/resources/db/migration`, named `V<n>__<description>.sql`
  (`V1__create_user_table.sql`, `V2__create_order_table.sql`, …). Never edit an
  applied migration. Because Flyway manages the schema, the Modulith
  `event_publication` table is **not** auto-created — provide a migration for
  it (`V1__event_publication.sql` already exists); same for the Quartz `QRTZ_*`
  tables (`V2__quartz_tables.sql`), since `spring.quartz.jdbc.initialize-schema`
  is `never`.
- **HTTP API**: base path `/api/v1/...`; every response body is an envelope
  `ApiResponse<T>` from `platform.tools`; errors are produced by
  `GlobalExceptionHandler` (see §6). Response DTOs are wrapped in
  `internal/delivery/http/res`.
- **Inter-module communication**:
  - *Synchronous* — facade in `<module>.api`; consumer declares
    `allowedDependencies = "<module>::API"`.
  - *Asynchronous / decoupled* — publish a Spring application event (a record
    in `<module>.api/event/`). Consumers listen with
    `@TransactionalEventListener(phase = AFTER_COMMIT)` in their own
    `internal/listener`. Consumers still declare `allowedDependencies =
    "<module>::API"` because they reference the event type.
- **No manual registration.** Components are picked up by component scanning;
  controllers are beans in `internal/delivery/http`. Do not hand-wire beans in
  `@Configuration` unless truly required.

---

## 4. Repository & resources layout (current + target)

```
src/
├── main/
│   ├── java/com/gepe/starter/...        # modules: platform, <feature modules>
│   └── resources/
│       ├── application.yaml             # core config (Spring Boot 4) incl. dev defaults (§5)
│       ├── db/migration/                # Flyway: V1__..., V2__... (created w/ first module)
│       ├── i18n/                        # see §6
│       └── logback-spring.xml           # logging: console default + `json` profile (§7)
└── test/java/com/gepe/starter/...       # tests incl. ModularityTests (§8)
Dockerfile                               # app image (planned) — the only container artifact
```

No `compose.yaml` anywhere (see §5). Do not invent infra files inside a module;
infrastructure lives either in `platform` (code) or at the repository root.

---

## 5. Environment & infrastructure

- **This repository never contains a `docker compose` file.** PostgreSQL and
  Redis are provided externally: they are already running locally (user-managed
  Docker containers outside this repo). The only container artifact in the repo
  is a `Dockerfile` at the repository root that packages the application.
- Local dev connection defaults are configured directly in
  `src/main/resources/application.yaml`, each wrapped in a `${VAR:default}`
  placeholder so every value can be overridden via the matching `SPRING_*`
  environment variable at run time. Never commit real secrets — use env vars in
  shared/CI environments:
  - PostgreSQL on `localhost:5432`, database **`starter-modulith`**,
    username/password **`root`**/`root` (`SPRING_DATASOURCE_URL`,
    `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`).
  - Redis on `localhost:6379`, username/password **`root`**/`root`
    (`SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`,
    `SPRING_DATA_REDIS_USERNAME`, `SPRING_DATA_REDIS_PASSWORD`).
- Integration/slice tests that need Postgres/Redis use the same running local
  services (see §8) — start them before running tests.

---

## 6. i18n conventions

Physical layout — **one folder for app-wide messages, one folder per module**:

```
src/main/resources/i18n/
├── messages/
│   ├── messages.properties        # DEFAULT locale (English) — app-wide keys only
│   └── messages_id.properties     # optional example of a second language
├── user/
│   ├── messages.properties        # all messages owned by module `user`
│   └── messages_id.properties
├── order/
│   ├── messages.properties
│   └── messages_id.properties
└── <new module>/messages.properties   # create one folder per new module
```

Rules:

- **File naming:** every folder holds files named `messages.properties` (the
  default/English bundle) plus `messages_<lang>.properties` for other languages
  (e.g. `messages_id.properties`). The folder only decides *who owns the keys*;
  a MessageSource registers one basename per folder: `i18n/<folder>/messages`.
- **`messages` is global/app-wide** — it owns generic *validation*, *HTTP* and
  *non-module errors* only. Examples: `jakarta.validation.constraints.NotNull.message`,
  `error.validation.request-invalid`, `error.http.404`, `error.internal`.
  **Never put module-specific text here.**
- **Module folders own everything about their module** (file
  `i18n/user/messages.properties`), keys prefixed with the module name:
  `user.not-found=User with id {0} was not found`,
  `user.validation.name.required=Name is required`.
  **Never put non-module text in a module folder.**
- Key collision is a bug: keys in `messages` have no prefix; keys in a module
  folder MUST start with `<module>.`.
- The app resolves every message through **one aggregated `MessageSource`**
  (a bean in `platform/i18n`, planned) that registers all basenames found under
  `i18n/*/` — global `messages` first, then modules alphabetically — with
  UTF-8 encoding. Code (controllers, exception handler, listeners) never reads
  `ResourceBundle` directly; it always injects the single `MessageSource` /
  a small `MessageResolver` helper.
- **Default locale is English.** Do not rely on the system locale;
  `Accept-Language` selects the language (English when absent/unsupported).
- **Errors are localized by code, not by exception class per module.** Feature
  modules throw platform exceptions with a code:
  `throw new NotFoundException("user.not-found", userId);` The
  `GlobalExceptionHandler` (platform) resolves the code through the aggregated
  MessageSource, so the *text* stays in the owning module's bundle while the
  handling code stays in platform (no platform → module dependency).

---

## 7. Logging & observability

Scope for this starter: **best-practice logging now, structured so OpenTelemetry
/ Prometheus / Grafana can be bolted on later without touching log statements.**

Rules for every log statement:

- Log at the right level: `debug` for detail, `info` for lifecycle/business
  milestones, `warn` for recoverable anomalies, `error` for failures (always
  pass the exception as last argument to keep the stack trace).
- Use parameterized messages, never string concatenation inside log calls:
  `log.info("User created: id={}, email={}", id, email);`
- Never log secrets, tokens, passwords, full request bodies, or credit
  card-like data. Log identifiers and counts, not payloads.
- Logger names come from the class package — the module attribution is free
  (e.g. `com.gepe.starter.user.internal.service.UserServiceImpl`).

Correlation context (MDC) — implemented in `platform`:

- `CorrelationIdFilter` (`platform/web`, a `@Component` filter bean at highest
  precedence) reads `X-Request-Id` or generates a UUID v7
  (`UuidCreator.getTimeOrderedEpoch()`, see §3 for the id policy), stores it in
  MDC under **`requestId`** and echoes it back in the response header
  (`X-Request-Id`). Every log line inside the request carries it. MDC key
  constants live in `platform/logging/MdcKeys`; the filter removes the key in a
  `finally` block.
- Additional MDC keys when meaningful: `userId` (after authentication) and
  `module`. Never write to MDC without clearing it afterwards.
- Async work (Quartz jobs, listeners, thread pools) must set/restore MDC
  explicitly; do not assume context propagates.

`logback-spring.xml` (in `src/main/resources`, implemented):

- Default profile: human-readable console pattern that includes
  `%X{requestId}` (and `%X{traceId}` when tracing is on); the key prints as
  `-` outside a request scope.
- A `json` profile (`--spring.profiles.active=json`) emits structured JSON logs
  (Logstash-style) containing the MDC fields — ready to be shipped to
  Loki/ELK. Uses `net.logstash.logback:logstash-logback-encoder`, already a
  dependency in the POM.

Later enablement (out of scope for the starter, do not implement now):

1. Metrics: add the Prometheus registry, keep actuator, expose
   `/actuator/prometheus`; scrape from Prometheus, dashboards in Grafana.
2. Tracing: add a Micrometer tracing bridge (e.g. OTel) + OTLP exporter and
   `management.tracing.*` config. Micrometer then fills MDC `traceId`/`spanId`
   automatically, correlating logs with traces — no code change required here.
   The observability auto-configuration is already on the classpath but kept
   disabled via `management.tracing.enabled: false` in `application.yaml`
   (flip it to `true` when the bridge is added).

---

## 8. Testing & enforced boundaries

Module boundaries are enforced by **`ModularityTests`** (already in the
repository at `src/test/java/com/gepe/starter/ModularityTests.java`) — keep it
green:

```java
class ModularityTests {

    private final ApplicationModules modules =
            ApplicationModules.of(SpringModulithStarterApplication.class);

    @Test
    void verifyModularity() {
        modules.verify(); // throws on any violation
    }

    @Test
    void writeDocumentationSnapshot() {
        new Documenter(modules).writeDocumentation();
    }
}
```

`modules.verify()` catches: referencing another CLOSED module's internal types,
undeclared module dependencies, and wrong named-interface usage.

> Because `platform` is OPEN (§2.4), Modulith does **not** check `platform`'s
> own outgoing dependencies — the "platform must not depend on feature modules"
> rule remains a code-review convention.

The intra-module rule "`api` must never depend on `internal`" (§2.1) is also
not machine-checked — follow it during code review. If machine enforcement is
wanted later for those two rules, add ArchUnit tests (ArchUnit is available
transitively via `spring-modulith-core`); it is intentionally not part of the
starter right now.

Further test conventions:

- Context/slice tests that need Postgres/Redis use the locally running services
  from §5 (`@SpringBootTest` / MockMvc as needed). Start them before running
  tests.
- i18n unit tests assert default-locale (en) and non-default (e.g. `id`)
  resolution for a representative key per bundle.
- Every behavior test should assert the observable outcome (status code,
  envelope, message), not implementation internals.

---

## 9. How to add a new module (definition of done)

1. Create `com.gepe.starter.<module>` with `package-info.java`
   (`@ApplicationModule(id = "<module>", allowedDependencies = ...)`), listing
   every other feature module it calls, always as `"<other>::API"`.
2. Create the `api` subpackage with `@NamedInterface("API")` `package-info`;
   put the facade interface, DTO records and event records there. Keep it free
   of `internal`, JPA, and Web types.
3. Create the `internal` skeleton: `entity`, `repository`, `service`
   (facade implementation), `delivery/http` with `req/`, `res/` and the
   controller. Add extra `internal` sub-packages only if genuinely needed.
4. Create `src/main/resources/i18n/<module>/messages.properties` (English
   default; more languages as `messages_<lang>.properties`) and prefix every
   key with `<module>.`.
5. Add a Flyway migration if the module owns new tables.
6. If another module must consume this module, add `"<module>::API"` to that
   consumer's `allowedDependencies`.
7. Run `./mvnw test` — `ModularityTests` must stay green; add/extend tests for
   the new module.
8. Nothing else to register: no manual bean wiring, no message-source config
   change (aggregation is automatic by folder scan).

---

## 10. Quick answers (TL;DR)

- *May `api` know `internal` inside the same module?* **No.** `internal`
  implements `api`; dependencies flow inward. (Cross-module internal access is
  enforced by `ModularityTests`; the intra-module `api` → `internal` rule is a
  code-review convention — §2.1, §8.)
- *May `internal` know `api`?* **Yes** — that is exactly how the facade is
  implemented and how the HTTP layer calls the module's own API.
- *What may other modules access?* Only `<module>.api` (`NamedInterface
  "API"`), and only when declared via `allowedDependencies = "<module>::API"`.
- *What is `platform`?* Shared module declared `@ApplicationModule(type =
  ApplicationModule.Type.OPEN)` so all its sub-packages are usable everywhere;
  by convention it must never depend on feature modules (§2.4).
- *Where do messages live?* App-wide folder `i18n/messages/` plus one folder
  per module `i18n/<module>/`; inside every folder the files are always named
  `messages.properties` (+ `messages_<lang>.properties`); module keys are
  `<module>.`-prefixed and resolved through one aggregated `MessageSource`
  (§6).
- *Logging?* SLF4J + MDC (`requestId`, …), parameterized messages, JSON-ready
  via logback profiles — OTel/Prometheus added later without code changes.
