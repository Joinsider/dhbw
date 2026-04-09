# Project: DHBW Dualis KMP

## Vision
To provide a complete, feature-rich alternative to the official `dualis.dhbw.de` portal, offering parity with all its features including grades, timetables, and documents, while maintaining a smooth and responsive experience across all supported platforms.

## Core Objectives
- **Feature Parity:** Implement all features available on the Dualis webpage (grades, timetable, documents, etc.).
- **Cross-Platform:** Maintain a single codebase using Kotlin Multiplatform for Android, iOS, and Desktop.
- **Resilience:** Handle the challenges of a non-official API (HTML scraping) through robust parsing and error handling.
- **Security:** Ensure secure connections to the DHBW servers, even on legacy devices.

## Current State (v2.0 Shipped)

**Shipped:** 2026-04-09  
**Phases:** 2, 3, 6, 7, 7.1 complete (7 phases, 16 plans)  
**What's Delivered:**
- Document scraping and data layer
- Document features UI with search and download
- Documents page navigation and integration
- Loading animations and desktop file handling
- iOS FileViewer compilation fixes

**Known Gaps:**
- Phase 1 (SSL/TLS Connectivity) - incomplete
- Phase 4 (Final Validation) - not started
- Phase 5 (Recheck Implementation) - not started

See: `.planning/milestones/v2.0-ROADMAP.md` for full details.

## Tech Stack
- **Framework:** Kotlin Multiplatform (KMP)
- **UI:** Compose Multiplatform
- **Network:** Ktor 2.x/3.x
- **Parsing:** Regex-based HTML scraping
- **Platforms:** Android, iOS, Desktop (macOS/Windows)

---
*Last updated: 2026-04-09 after v2.0 milestone*
