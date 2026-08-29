# Phase 10: Android API Compliance (Weeks 8-10) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.  
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-10  
**Phase:** 10-Android API Compliance  
**Areas discussed:** Device Detection, Windows Insets, Foldable Support, Orientation Strategy

---

## Device Detection Strategy

**Context:** Phase requires support for large screens and foldables. Current implementation uses Configuration API (screenLayout + smallestScreenWidthDp). Question: continue with Configuration, or upgrade to WindowMetrics for better large-screen/foldable handling?

| Option | Description | Selected |
|--------|-------------|----------|
| Keep Configuration API | Stick with current screenLayout + smallestScreenWidthDp approach — simpler, already working | |
| Upgrade to WindowMetrics | Adopt Display.getWindowMetrics() for real-time bounds — better for foldables and large screens | |
| Hybrid approach | Use WindowMetrics primarily, fall back to Configuration if WindowMetrics unavailable — safest for older Android versions | ✓ |

**User's choice:** Hybrid approach  
**Rationale:** Provides precision for modern devices (WindowMetrics) while maintaining compatibility with older Android versions via Configuration fallback.

---

## Windows Inset Handling

**Context:** `enableEdgeToEdge()` is already called in MainActivity. Question: rely on default Compose inset handling, or manually apply WindowInsets padding?

| Option | Description | Selected |
|--------|-------------|----------|
| Default (Rely on enableEdgeToEdge) | System draws under system bars; Compose respects insets automatically | ✓ |
| Manual Inset Application | Call WindowInsets.systemBars.asPaddingValues(), apply as padding/margin to layouts | |

**User's choice:** Default (enableEdgeToEdge + Compose automatic handling)  
**Rationale:** Material Design default is battle-tested; Compose respects insets automatically in most layouts; avoids over-engineering.

---

## Foldable Support

**Context:** Phase requirements mention "Implement hinge position detection for foldable devices." Question: depth of support?

| Option | Description | Selected |
|--------|-------------|----------|
| Large-screen support only | Foldables work via tablet/large-screen logic; no special hinge handling | |
| Basic hinge detection | Detect fold state, allow layouts to adapt when folded/unfolded | ✓ |
| Hinge-aware responsive layouts | Full implementation: respond to hinge position, split content around fold line | |

**User's choice:** Basic hinge detection  
**Rationale:** Balances compliance with pragmatism — detects fold state without over-engineering responsive layouts around hinge position.

---

## Orientation Management

**Context:** Phase requires removing hard portrait lock for Android 16+ and supporting dynamic orientation based on window size. Question: sensor-driven rotation or window-size-driven adaptation?

| Option | Description | Selected |
|--------|-------------|----------|
| Sensor-driven (user preference) | Allow system to rotate based on accelerometer; respect Settings > Display > rotation preference | ✓ |
| Window-size driven | Adapt orientation dynamically based on current window aspect ratio; no sensor rotation | |
| Hybrid approach | Sensor-driven but constrained by available window dimensions; adapt to fold state dynamically | |

**User's choice:** Sensor-driven (user preference)  
**Rationale:** Respects Android 16+ compliance requirements; follows user's system settings; standard Android practice.

---

## Claude's Discretion

No areas were deferred to Claude.

---

## Deferred Ideas

None — discussion stayed within Phase 10 scope.

---

*Phase: 10-android-api-compliance*  
*Discussion completed: 2026-04-10*
