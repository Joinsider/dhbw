# Project: DHBW Dualis KMP

## Vision
To provide a complete, feature-rich alternative to the official `dualis.dhbw.de` portal, offering parity with all its features including grades, timetables, and documents, while maintaining a smooth and responsive experience across all supported platforms.

## Core Objectives
- **Feature Parity:** Implement all features available on the Dualis webpage (grades, timetable, documents, etc.).
- **Cross-Platform:** Maintain a single codebase using Kotlin Multiplatform for Android, iOS, and Desktop.
- **Resilience:** Handle the challenges of a non-official API (HTML scraping) through robust parsing and error handling.
- **Security:** Ensure secure connections to the DHBW servers, even on legacy devices.

## Tech Stack
- **Framework:** Kotlin Multiplatform (KMP)
- **UI:** Compose Multiplatform
- **Network:** Ktor 2.x/3.x
- **Parsing:** Regex-based HTML scraping
- **Platforms:** Android, iOS, Desktop (macOS/Windows)
