# Project: DHBW Dualis KMP

## Vision
A complete, feature-rich alternative to the official `dualis.dhbw.de` portal — grades, timetable
and documents — that stays smooth and responsive on every platform it ships to.

## Core Objectives
- **Feature Parity:** everything the Dualis web portal offers (grades, timetable, documents).
- **Cross-Platform:** one Kotlin codebase for Android, iOS, Desktop and macOS. Shared down to the
  presentation layer; the UI is per platform.
- **Resilience:** the portal has no API. Parsing and error handling have to survive an HTML page
  that changes without notice.
- **Security:** secure connections to the DHBW servers, including on JVMs whose trust store does
  not know the certificate chain Dualis uses.

## Current State (v3 architecture rebuild, P-1…P8 complete)

The v3 work is an architecture rebuild, not a feature release. It is not on `main`: everything
lives on the local `v3` branch and nothing has been pushed.

**Shape of the codebase** — seven modules, package root `de.fampopprol.dhbwhorb` in every one:

```
:core:common     Outcome/AppError, platform detection, appCoroutineScope
:core:testing    fakes and mocks for every module's tests; nothing in production uses it
:domain          models, the six repository interfaces, use cases, TimeHelper
:data            Ktor, HTML parsers, Dualis services, Room + DAOs, SecureStorage, repositories
:services        notifications, lecture reminders, widget use cases
:presentation    MVI stores (State / Intent / Msg / Effect) — no Compose, by rule
:shared          umbrella; exports the above as Shared.framework for Apple targets
:composeApp      Compose UI for Android, Desktop and macOS, plus the Glance widget
iosApp/          the SwiftUI app and the timetable widget extension
```

**What the rebuild delivered:** a typed error model instead of thrown exceptions, Koin as the
single composition root, MVI stores shared by both UIs, real Room migrations with a test that
proves them, a native SwiftUI app on iOS with its own widget reading the App-Group database, and
background work on all four platforms — hourly change detection plus reminders scheduled by the
operating system rather than polled.

**Where the detail is:** `.planning/v3-ARCHITECTURE-PLAN.md` (the plan and what each phase
actually did), `AGENTS.md` (the current architecture — it wins over the plan where they differ),
and `.planning/HANDOFF.md` (environment, gate, pitfalls, next step).

**Remaining:** P9, the cleanup phase. Beyond it, the open points in `.planning/HANDOFF.md` §6 —
most of them wait for a real device or for a decision, not for code.

## Earlier milestones

- **v2.0** (shipped 2026-04-09): document scraping, the documents UI, download and file handling.
  See `.planning/milestones/v2.0-ROADMAP.md`.
- **v3.0 "Stability & Compliance"** — the ANR, API-compliance and pipeline work in
  `.planning/ROADMAP.md` and `.planning/phases/08…13`. Done, and separate from the architecture
  rebuild above, which took over the name `v3` afterwards. `.planning/STATE.md` still tracks that
  earlier milestone; the architecture rebuild is tracked in its own plan.

## Tech Stack
- **Framework:** Kotlin Multiplatform, Kotlin 2.3
- **UI:** Compose Multiplatform on Android, Desktop and macOS; SwiftUI on iOS
- **DI:** Koin, one graph started from `:shared`
- **Storage:** Room (bundled SQLite) plus platform secure storage for credentials
- **Network:** Ktor 3
- **Parsing:** regex-based HTML scraping, covered by fixture tests
- **Platforms:** Android, iOS, Desktop (JVM), macOS

---
*Last updated: 2026-08-22, during P9.*
