# ADR-0003: App-Badge entfernen, bis die App zeigt, was sich geändert hat

- **Status:** Angenommen
- **Datum:** 2026-10-29
- **Ersetzt:** [ADR-0001](0001-app-badge-ungesehene-aenderungen.md)
- **Betrifft:** `:services` (Notifications), `:shared` (`SharedApp`), `iosApp/` (iOS und macOS),
  `composeApp` (Android)

## Kontext

ADR-0001 hat die hängende Badge behoben: Sie zählt seither die seit dem letzten Öffnen gemeldeten
Stundenplan-Änderungen und geht beim Öffnen auf 0. Der Nachtrag dort hat das Zählen in eine Klasse
(`AppBadge`) und einen umhüllenden Dispatcher (`BadgingNotificationDispatcher`) gelegt.

Die Support-Anfrage, mit der ADR-0001 begann, löst das aber nur zur Hälfte. Sie lautete sinngemäß:
*„Die App zeigt zwei offene Mitteilungen, aber wenn ich sie öffne, sehe ich keine ungeöffneten
Nachrichten.“* Eine Zahl am Icon verspricht genau das: dass in der App etwas auf einen wartet.
Die App hat dafür keinen Ort. Der Stundenplan zeigt beim Öffnen den neuen Stand und hebt nichts
hervor; die Zahl verschwindet, ohne dass sichtbar wird, worauf sie sich bezog. Das Missverständnis
der Anfrage bleibt also bestehen — es fällt nur nicht mehr auf, weil die Zahl nicht mehr hängt.

Dazu kommt, dass die Zahl nicht alles zählt, was als Benachrichtigung ankommt. Erinnerungen an
Vorlesungen hält das System selbst und zeigt sie, ohne die App zu wecken; sie können nicht mitzählen.
Fachlich ist das richtig — eine Erinnerung meldet keine Änderung —, für den Nutzer aber eine Zahl,
die mal stimmt und mal nicht.

Für diesen Nutzen trägt die App: einen Zähler in den Settings, eine eigene Klasse mit Mutex, einen
Dispatcher-Wrapper mit eigenem Koin-Qualifier, eine Anzeige pro Plattform und Rücksetzpunkte in
`RootView` (iOS/macOS) und `MainActivity` (Android).

## Entscheidung

**Die App setzt keine Badge mehr. Benachrichtigungen bleiben unverändert und liegen weiter in der
Mitteilungszentrale.**

1. **Entfernen:** `AppBadge`, `BadgeDisplay`, `AppleBadgeDisplay`, `BadgingNotificationDispatcher`,
   der Qualifier `PlatformNotificationDispatcher`, der Zähler `unseen_lecture_changes` samt Getter
   und Setter in `NotificationPreferences`/`NotificationPreferencesInteractor`, `markLectureChangesSeen`
   in `SharedApp`, das Rücksetzen in `RootView` und in `MainActivity.onResume`. Die Plattformmodule
   binden ihren Dispatcher wieder direkt als `NotificationDispatcher`.
2. **Keine Badge-Berechtigung mehr:** `.badge` bzw. `UNAuthorizationOptionBadge` fällt aus den
   Berechtigungsanfragen (`SettingsScreen.swift`, `IosNotificationDispatcher`,
   `MacosNotificationDispatcher`).
3. **Einmal aufräumen:** Wer die Berechtigung schon erteilt hat, behält sie, und eine Zahl, die eine
   frühere Version gesetzt hat, bleibt am Icon stehen — die Person aus der Anfrage hätte ihre „2“
   sonst für immer. Beim Start setzt die App deshalb auf iOS und macOS die Badge einmal auf 0
   (`setBadgeCount(0)`). Das ist die einzige Badge-Zeile, die bleibt, und sie ist als Aufräumen für
   ältere Versionen kommentiert.
4. **Bleibt erhalten:**
   - Die iOS-Hintergrundaufgabe für die verzögerte Testbenachrichtigung („In 10 Sekunden“) — sie
     macht die Zustellung zuverlässig, unabhängig von der Badge.
   - Androids `setNumber` an der Sammelbenachrichtigung: Das ist die Zahl *an der Benachrichtigung*,
     keine App-Badge, und verschwindet mit ihr.
   - `cancelAllDelivered()` beim Logout.

## Betrachtete Alternativen

### A — ADR-0001 mit `AppBadge` beibehalten

Technisch sauber und getestet. **Verworfen**, weil die Zahl auf etwas zeigt, das die App nicht
zeigt; der Aufwand bleibt, das Missverständnis auch.

### B — Erinnerungen mitzählen

Mit `content.badge` beim Planen ließe sich eine Zahl mitgeben, aber sie wäre beim Planen festgelegt
und stimmt nicht mehr, sobald dazwischen etwas anderes zählt oder die App geöffnet wird. **Verworfen**:
mehr Komplexität für eine Zahl, die öfter falsch wäre.

### C — Bereich „Änderungen“ in der App, Badge = ungelesene Einträge

Die eigentliche Antwort auf die Anfrage (Alternative C aus ADR-0001). **Zurückgestellt.** Kommt sie,
bekommt die Badge eine eigene ADR, und die Zahl hat dann einen Ort, auf den sie zeigt.

## Konsequenzen

**Positiv**
- Die Fehlerklasse, mit der die Anfrage begann, kann nicht mehr auftreten.
- Weniger Code und ein Koin-Graph ohne Qualifier und Wrapper.
- Keine Berechtigung mehr, die die App nicht braucht.

**Negativ**
- Wer eine Benachrichtigung wegwischt, sieht am Icon nicht mehr, dass sich etwas geändert hat.
  Akzeptiert bis Alternative C.
- Eine einmalige Aufräumzeile beim Start, die ältere Versionen voraussetzt; sie kann entfallen,
  wenn keine Version vor dieser mehr im Umlauf ist.

**Tests**
- `AppBadgeTest` entfällt mit der Klasse.
- `KoinGraphTest` ohne `BadgeDisplay`.
- Manuell im Simulator: Benachrichtigung senden, Icon zeigt keine Zahl; eine vorher gesetzte Zahl
  verschwindet beim ersten Start.
