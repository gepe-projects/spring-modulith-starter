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
| Multi-instance | Clustered Quartz (JDBC), DB-backed event registry + staleness/restart republish, stateless instances — see §11 |

Commands:

```bash
./mvnw test          # unit + modularity + (later) slice/integration tests
./mvnw verify        # everything, incl. packaging
./mvnw spring-boot:run   # run app locally (needs Postgres/Redis up)
```

**Current state:** the `platform` module (shared, OPEN) holds `web/`,
`logging/`, `exception/`, `web/response/` and `i18n/` — request-scoped MDC
logging (`CorrelationIdFilter` + `logback-spring.xml`, §7) and the complete
i18n + error-handling foundation of §2.4/§6: aggregated `MessageSource` +
locale policy (default English), `MessageHelper`, `ErrorCode` contract with
`GlobalError` + per-module error enums, `ServiceException` /
`ValidationException`, `ApiResponse` / `ErrorResponse` / `ValidationError`
envelopes, and the `GlobalExceptionHandler` (`@RestControllerAdvice`) that
turns every Spring MVC and validation exception into the localized envelope.
Plus the **example feature module `user`** (§2/§9 — create + read user with
`api`/`internal` split, module error enum, module i18n bundle, event +
listener, Flyway `V3`, behavior tests), a `README.md` quickstart, and the
multi-instance configuration of §11 (Quartz clustered, event registry
recovery, health groups). `SpringModulithStarterApplication` is registered
with `@Modulith(sharedModules = "platform")`; `ModularityTests` verifies the
real `user`-module boundaries. Context-load tests need the local Postgres/Redis
from §5 up (dev defaults in `application.yaml`).
Still planned: more feature modules, `Dockerfile`, `platform/config` &
`platform/persistence` (cache manager, auditing/`BaseEntity`), further Quartz
job examples, metrics/tracing enablement (§7), Spring Security.
Everything below is **normative**: apply these conventions as modules, tests,
and infrastructure are implemented. Pieces that are still planned are
explicitly flagged "(planned)".

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
  (`config`, `web`, `exception`, `i18n`, …) are accessible from every other
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
logic. Sub-packages (implemented unless marked "(planned)"):

```
platform/
├── config/          # (planned) @Configuration: Redis/cache manager (requires adding
│   │                #   spring-boot-starter-cache; store MUST be Redis — §11),
│   │                #   JPA auditing, scheduling
├── web/             # GlobalExceptionHandler (@RestControllerAdvice, §6), CorrelationIdFilter,
│   │                #   WebConfig (LocaleResolver: Accept-Language, default English, §6)
│   └── response/    # API envelopes: ApiResponse<T>, ErrorResponse, ValidationError (§6)
├── exception/       # ErrorCode contract, GlobalError (shared codes), ServiceException,
│                    #   ValidationException — modules add their own ErrorCode enums (§6)
├── i18n/            # I18nConfig (aggregated MessageSource, bean name "messageSource"),
│                    #   MessageHelper helper (§6)
├── persistence/     # (planned) BaseEntity (@MappedSuperclass) + auditing support
└── logging/         # MDC key constants / helpers (see §7)
```

`platform` may depend on Spring/third-party libraries and the JDK — never on
`com.gepe.starter.<feature module>`.

---

## 3. Implementation conventions (Java)

- **Lombok is the convention for standard class boilerplate.** Use `@Slf4j`
  for loggers, `@RequiredArgsConstructor` for constructor injection, and
  `@Getter` (plus `@Setter`, `@Builder`, `@NoArgsConstructor` /
  `@AllArgsConstructor` as needed) on mutable classes and enums. Do not
  hand-write these — hand-written boilerplate is not "more readable", it only
  diverges from the ecosystem convention (same style as the
  `spring-boot-starter-auth` project). Never `System.out`.
- **DTOs, commands, events, api results, and response envelopes are
  `record`s** (immutable) — records stay plain Java, no Lombok. JPA entities
  are **not** records — use Lombok (`@Getter`, `@Setter`,
  `@NoArgsConstructor` / `@AllArgsConstructor`, `@Builder`) as appropriate.
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
  (Not yet enabled: requires adding `spring-boot-starter-cache` +
  `spring.cache.type: redis`; the store MUST be shared Redis, never an
  in-process cache — instances must serve identical data, §11.)
- **Flyway**: one migration per schema change under
  `src/main/resources/db/migration`, named `V<n>__<description>.sql`
  (`V1__create_user_table.sql`, `V2__create_order_table.sql`, …). Never edit an
  applied migration. Because Flyway manages the schema, the Modulith
  `event_publication` table is **not** auto-created — provide a migration for
  it (`V1__event_publication.sql` already exists); same for the Quartz `QRTZ_*`
  tables (`V2__quartz_tables.sql`), since `spring.quartz.jdbc.initialize-schema`
  is `never`.
- **HTTP API**: base path `/api/v1/...`; every response body is an envelope
  from `platform.web.response` — `ApiResponse<T>(message, data)` on success
  and `ErrorResponse(code, message, errors)` on failure (both implemented, see
  §6); errors are produced by `GlobalExceptionHandler` (see §6). Response DTOs
  are wrapped in `internal/delivery/http/res`.
- **Inter-module communication**:
  - *Synchronous* — facade in `<module>.api`; consumer declares
    `allowedDependencies = "<module>::API"`.
  - *Asynchronous / decoupled* — publish a Spring application event (a record
    in `<module>.api/event/`). Consumers listen with
    `@TransactionalEventListener(phase = AFTER_COMMIT)` in their own
    `internal/listener`. Consumers still declare `allowedDependencies =
    "<module>::API"` because they reference the event type. Execution of such
    listeners is tracked in `event_publication` and delivery is at-least-once
    across instances — listeners must be idempotent (§11).
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

Physical layout — **one folder for app-wide messages, one folder per module**
(`src/main/resources/i18n/messages/messages.properties` exists and is the
current catalogue: `common.*`, `http.*`, `db.*`, `file.*`, `pagination.*`,
`system.*`, `validation.failed`, and the Jakarta/Hibernate constraint keys):

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
  the MessageSource registers one basename per folder: `i18n/<folder>/messages`.
- **`messages` is global/app-wide** — it owns generic *validation*, *HTTP* and
  *non-module errors* only. Examples: `jakarta.validation.constraints.NotNull.message`,
  `validation.failed`, `http.not_found`, `system.error`,
  `db.duplicate_entry`. **Never put module-specific text here.**
- **Module folders own everything about their module** (file
  `i18n/user/messages.properties`), keys prefixed with the module name:
  `user.not-found=User with id {0} was not found`,
  `user.validation.name.required=Name is required`.
  **Never put non-module text in a module folder.**
- Key collision is a bug: keys in `messages` have no prefix; keys in a module
  folder MUST start with `<module>.`. Values may carry `{0}`, `{1}`…
  placeholders for message arguments.
- The app resolves every message through **one aggregated `MessageSource`**:
  `I18nConfig` (`platform/i18n`) exposes a bean **named exactly `messageSource`**
  (Boot's auto-configuration then backs off) that registers one basename per
  folder found under the classpath directory `i18n` — global `messages` first,
  then module folders alphabetically — encoded UTF-8, default locale English,
  no fallback to the system locale. Adding a module folder needs **no config
  change** (discovery is automatic). Code never reads `ResourceBundle`
  directly; it injects the `MessageSource` or the small `MessageHelper`
  component (`get(code, args…)` — resolves for the current request locale;
  missing keys are logged and the key itself is returned so typos surface
  fast).
- **Default locale is English.** `WebConfig` (`platform/web`) registers an
  `AcceptHeaderLocaleResolver`: `Accept-Language` selects the language among
  the configured supported locales (today only English); a missing or
  unsupported header falls back to English, never to the system locale.
  Extend the supported list only together with the matching
  `messages_<lang>.properties` bundles.
- **Validation messages flow through the same source.** Boot's auto-configured
  validator interpolates constraint messages against the context
  `messageSource` with the request locale (verified on Boot 4.1.1), so the
  constraint keys above and module keys referenced from annotations
  (`@NotBlank(message = "{user.validation.name.required}")`) localize without
  extra wiring.

Error codes, envelope & handling — one advice (`platform/web/GlobalExceptionHandler`)
for the whole application; modules must not define their own:

- **Every module declares its own error enum implementing the `ErrorCode`
  contract** (`platform/exception`): one constant = HTTP status + message key.
  Put it in `internal/exception/<Module>Error.java` next to its bundle, e.g.
  for module `user`:

  ```java
  public enum UserError implements ErrorCode {
      USER_NOT_FOUND(HttpStatus.NOT_FOUND, "user.not-found"),
      NICKNAME_TAKEN(HttpStatus.CONFLICT, "user.nickname_taken");
      // + fields/constructor/getters implementing ErrorCode
  }
  ```

  Error codes are **not** global: `GlobalError` (`platform/exception`) holds
  only genuinely cross-cutting codes (database conflicts, `system.error`,
  …) that every module may reuse. Never add module-specific errors there.
- **Throw one exception type:** `throw new ServiceException(UserError.USER_NOT_FOUND, userId);`
  The advice reads the status and the message key from the error code and
  resolves the key (with its `{0}`, `{1}`… arguments) through the aggregated
  MessageSource — so the *text* stays in the owning module's bundle while the
  handling code stays in platform (no platform → module dependency).
  Service-layer field validations use `ValidationException(List<ValidationError>)`
  with already-localized messages (resolved via `MessageHelper` at the throw
  site).
- **Envelope** (in `platform/web/response`): success is
  `ApiResponse<T>(message, data)`; every failure is
  `ErrorResponse(code, message, errors)`:
  - `code` — stable, machine-readable error identifier. It always equals the
    resolved message key (e.g. `user.not-found`, `validation.failed`,
    `db.duplicate_entry`) and is what frontends use for branching logic.
    Message keys are therefore **public API**: renaming a key is a breaking
    change. Never derive logic from parsing `message`.
  - `message` — localized headline; the one text frontends render (toast /
    banner). Always present.
  - `errors` — `[{field, message}]`, present **only** for per-field validation
    failures (400), `null` otherwise. Form UIs bind these to inputs; other
    consumers just show `message`.
  Per-field text from bean validation comes from
  `FieldError.getDefaultMessage()` / `ConstraintViolation.getMessage()` and is
  already localized. `null` fields are omitted from the JSON body
  (`@JsonInclude(NON_NULL)`).
- The advice maps **framework and validation exceptions** to codes from the
  app-wide bundle: validation (`MethodArgumentNotValidException`/`BindException`,
  `HandlerMethodValidationException`, `ConstraintViolationException`,
  `ValidationException`) → 400 `validation.failed` + `errors`; malformed body
  with a Jackson format problem → 400 `validation.failed` + one field error
  (`http.invalid_enum_field` / `http.invalid_field_value` text), plain
  malformed body → 400 `http.bad_request`; type mismatch → 400
  `http.invalid_param_value`; missing parameter → 400 `http.parameter_required`;
  no handler → 404 `http.not_found`; wrong method →
  405 `http.method_not_allowed`; media type problems → 406/415; upload too
  large → 413 `file.too_large`; `DataIntegrityViolationException` → 409
  `db.duplicate_entry` (duplicate) or 400 `db.data_integrity`;
  `OptimisticLockingFailureException` → 409 `exception.optimistic_lock`;
  anything unexpected → 500 `system.error` (logged `error` with stack; the
  client never sees internals). Add security handlers (e.g. access denied)
  together with the Spring Security dependency once it arrives.
- Logging policy inside the advice: expected business outcomes and validation
  failures → `debug`; contract violations (4xx) → `warn` without stack trace;
  unexpected failures → `error` with the full stack trace. The MDC `requestId`
  is present on every line.

---

## 7. Logging & observability

Scope for this starter: **best-practice logging now, structured so OpenTelemetry
/ Prometheus / Grafana can be bolted on later without touching log statements.**

Rules for every log statement:

- Loggers are declared with Lombok `@Slf4j` (see §3) — the logger name comes
  from the class package automatically; never hand-write a Logger field.
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
   key with `<module>.`. Declare the module's error codes as
   `<Module>Error implements ErrorCode` in `internal/exception/` (§6) so
   services throw `new ServiceException(<Module>Error.X, args…)`.
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
- *Can the application run in multiple instances?* **Yes** — clustered Quartz,
  DB-backed event registry with crash recovery, stateless instances; see §11.

---

## 11. Multi-instance (clustered) operation — normative

The starter is designed and configured to run **several instances against the
same PostgreSQL/Redis** (rolling deploys, scale-out). The rules below are
binding; §1 state & `application.yaml` implement them. How to try it: run two
instances, e.g. `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8080`
and `…--server.port=8081` (same DB/Redis), then create a user via one instance
and read it via the other; watch the Quartz cluster in the logs.

### 11.1 What is already safe (configuration)

- **Scheduler — Quartz clustered.** JDBC job store (`job-store-type: jdbc`,
  `initialize-schema: never`; tables from `V2__quartz_tables.sql`), one shared
  `scheduler-name: appScheduler`, `instanceId: AUTO`, `isClustered: true`,
  `acquireTriggersWithinLock: true` (recommended for PostgreSQL). Quartz
  guarantees each trigger fires on exactly one instance. **Never use
  `@Scheduled`** — every instance would fire. Scheduled work = Quartz job.
- **Modulith events — DB-backed registry.** Publishing an event writes one
  `event_publication` row per transactional listener **in the same transaction**
  (`V1__event_publication.sql`); completion is recorded after the listener ran.
  Crash recovery across instances (`application.yaml`):
  - `republish-outstanding-events-on-restart: true` — on startup **any**
    instance republishes every publication that is not `COMPLETED` (including
    rows left by a crashed instance).
  - `spring.modulith.events.staleness.*` (non-zero → monitor active) — each
    instance periodically marks publications stuck in `PUBLISHED` /
    `PROCESSING` / `RESUBMITTED` longer than the configured duration as
    `FAILED`.
  - **Automatic resubmission of FAILED publications** — the platform job
    `platform/modulith/EventPublicationResubmission*` re-delivers FAILED
    publications every 5 minutes through the clustered Quartz scheduler
    (exactly one instance; ≤10 listener attempts, min age 1 min, bounded
    batches). Listeners recover without waiting for a restart.
  - **Delivery is at-least-once and may duplicate** (crash between commit and
    listener, resubmission racing a slow listener). **Listeners must be
    idempotent.** The example `UserEventListener` is log-only on purpose.
  - Housekeeping: completed rows accumulate (completion mode `UPDATE`).
    For production, either set `spring.modulith.events.completion-mode: DELETE`
    or purge periodically via the `CompletedEventPublications` bean.
- **Flyway — concurrent boots safe.** PostgreSQL advisory locks serialize
  migrations across instances that start at the same time.
- **Database constraints are the cross-instance truth.** Uniqueness etc. is
  enforced by the database (e.g. `users.email`, `V3`); the service flushes
  after save so the constraint fires inside the transaction and the
  `GlobalExceptionHandler` maps it to a clean 409. Application-level pre-checks
  (e.g. `existsByEmail` before insert) race across instances and are **not**
  a substitute.
- **Health/readiness** — the readiness group includes `db` and `redis`, so a
  lost dependency takes the instance out of load-balancer rotation.

### 11.2 Rules for new code

1. **No `@Scheduled`** — Quartz jobs only. First example in the repo: the
   platform `modulithEventPublicationResubmission` job (see 11.1).
2. **No in-process state that must be shared**: no local caches, no
   `static` mutable state, no in-memory queues. MDC and request-scoped values
   never survive a request; async work (listeners, jobs) must set/restore MDC
   explicitly (§7).
3. **Caching (later) must use Redis** — when `@Cacheable`/`@CacheEvict` are
   added (requires `spring-boot-starter-cache` + `spring.cache.type: redis`,
   configured once in `platform/config`), the store is shared Redis, never an
   in-process cache: every instance must serve identical data. Cache only
   read paths returning api DTO records.
4. **Idempotent event listeners** (11.1) and idempotent jobs: effects that must
   happen exactly once need a DB-backed guard (e.g. unique constraint), not an
   in-memory flag.
5. **No instance affinity**: anything addressable (files, temp storage, uploads)
   must live outside the instance or be documented as unsupported. No
   `Dockerfile`/container artifacts inside modules; the only container artifact
   is the root `Dockerfile` (planned, §4).
6. Instance identity (logs, cluster) comes from config/env — never hard-code a
   port, host or instance name in code.

