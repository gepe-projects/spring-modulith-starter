# spring-modulith-starter

Starter *modular monolith* berbasis **Spring Boot 4 + Spring Modulith**:
satu aplikasi yang bisa di-deploy, dikembangkan sebagai modul-modul bisnis yang
batasnya **ditegakkan mesin** (bukan sekadar konvensi), lengkap dengan
fondasi platform (error-handling terpusat, i18n, logging terstruktur) dan
**siap berjalan multi-instance**.

> Dokumen konvensi lengkap (normatif): **[`agents.md`](agents.md)** — baca
> sebelum menambah modul. README ini hanya pintu masuk cepat.

## Isi starter

- **Contoh modul bisnis `user`** (`com.gepe.starter.user`) — kecil tapi utuh:
  `api`/`internal` split, facade `UserApi`, entity + repository JPA, service
  `@Transactional`, controller `/api/v1/users`, event domain + listener
  AFTER_COMMIT, error enum modul, bundle i18n milik modul, migrasi Flyway.
  Salin pola ini untuk modul berikutnya (lihat `agents.md` §9).
- **Modul `platform`** (shared, OPEN): envelope API
  `{message, data}` / `{code, message, errors}`, `GlobalExceptionHandler`,
  `ErrorCode` + `ServiceException`, agregasi `MessageSource` per-folder i18n,
  filter MDC `requestId` (UUID v7), logback profil `json`.
- **Infrastruktur siap produksi**: PostgreSQL + JPA + Flyway (migrasi
  `event_publication` & tabel Quartz sudah ada), Redis, Quartz JDBC-clustered,
  Actuator health/readiness, UUID v7 di-generate aplikasi.

## Stack

| Bagian | Pilihan |
|---|---|
| Java / Boot / Modulith | 25 / 4.1.1 / 2.1.1 |
| Build | Maven wrapper |
| Database | PostgreSQL (Flyway) |
| Secondary store | Redis |
| Scheduler | Quartz (JDBC, clustered) |
| Verifikasi batas modul | `ModularityTests` (`modules.verify()`) |

## Menjalankan

Prasyarat: PostgreSQL dan Redis yang bisa diakses (repo ini sengaja **tidak**
berisi `compose.yaml` — infrastruktur dikelola di luar repo, lihat `agents.md`
§5). Default lokal: `localhost:5432` db `starter-modulith` user/password
`root`/`root`, dan `localhost:6379` user/password `root`/`root` — semua bisa
di-override lewat env `SPRING_*` (lihat `.env` sebagai contoh, jangan commit
secret).

```bash
./mvnw spring-boot:run        # butuh Postgres & Redis jalan
./mvnw test                   # unit + modularity + integrasi (butuh Postgres & Redis)
```

Coba API contoh:

```bash
curl -i -X POST localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"name":"Budi","email":"budi@example.com"}'
# 201 {"message":"User created successfully","data":{"id":"…","name":"Budi","email":"budi@example.com"}}

curl -i localhost:8080/api/v1/users/00000000-0000-0000-0000-000000000000
# 404 {"code":"user.not-found","message":"User with id 00000000-… was not found"}

curl -i localhost:8080/api/v1/users/not-a-uuid
# 400 {"code":"http.invalid_param_value", …}
```

Logging JSON (Loki/ELK-ready): `SPRING_PROFILES_ACTIVE=json ./mvnw spring-boot:run`.

## Multi-instance

Aplikasi **aman & sengaja dirancang berjalan beberapa instance** pada
Postgres/Redis yang sama:

```bash
# terminal 1
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
# terminal 2
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

Jaminan (detail & aturan normatif di `agents.md` §11):

- **Quartz clustered**: trigger hanya dijalankan satu instance (JDBC store
  + `isClustered`, `acquireTriggersWithinLock`).
- **Event modulith at-least-once**: tiap event ditulis ke `event_publication`
  dalam transaksi yang sama; instance yang crash meninggalkan baris
  *incomplete* → *staleness monitor* menandai `FAILED`, restart instance mana
  pun me-*republish*, dan Quartz job platform
  `modulithEventPublicationResubmission` mengirim ulang publikasi `FAILED`
  otomatis tiap 5 menit (max 10 attempt) — listener wajib **idempotent**.
- **DB sebagai sumber kebenaran lintas-instance**: mis. keunikan `email`
  dijaga unique constraint + `flush()` di service, bukan pre-check yang bisa
  race. Migrasi Flyway aman jalan bersamaan (advisory lock Postgres).
- **Instance stateless**: id UUID v7 di-generate aplikasi (tanpa urutan
  bersama), tanpa cache in-memory — cache (nanti) wajib Redis.
- **Dilarang**: `@Scheduled` (pakai Quartz), cache lokal, state di memory
  antar-request. `readiness` health berisi `db` + `redis`.

## Menambah modul baru

1. `com.gepe.starter.<modul>` + `package-info.java` CLOSED
   (`allowedDependencies = "<lain>::API"`).
2. `api/` (`@NamedInterface("API")`): facade + DTO/event record.
3. `internal/`: `entity`, `repository`, `service`, `delivery/http` (+ `req`),
   `exception/<Modul>Error implements ErrorCode`, `listener`.
4. `src/main/resources/i18n/<modul>/messages.properties` (key berprefix
   `<modul>.`), migrasi Flyway bila ada tabel baru.
5. `./mvnw test` — `ModularityTests` wajib hijau.

Pola lengkap: salin modul `user`. Konvensi detail: `agents.md` §2, §9.

## Peta direktori

```
src/main/java/com/gepe/starter/
├── SpringModulithStarterApplication.java   # @Modulith(sharedModules = "platform")
├── platform/        # shared infra (OPEN): web, exception, i18n, logging,
│                    #   response, modulith (resubmission job)
└── user/            # contoh modul bisnis (CLOSED) — api/ + internal/
src/main/resources/
├── application.yaml  # config inti + multi-instance (Quartz, events, health)
├── db/migration/     # V1 event_publication, V2 Quartz, V3 users
├── i18n/             # messages/ (global) + user/ (milik modul)
└── logback-spring.xml
```

## Belum ada (planned)

`Dockerfile`, `platform/config` (cache manager Redis, butuh
`spring-boot-starter-cache`) & `platform/persistence` (`BaseEntity` + audit),
contoh Quartz job lain, metrics Prometheus & tracing OTel (lihat `agents.md`
§7), Spring Security (starter terpisah). Semua tercatat eksplisit di
`agents.md`.
