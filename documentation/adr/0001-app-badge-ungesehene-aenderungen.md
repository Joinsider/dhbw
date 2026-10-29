# ADR-0001: App-Badge zählt ungesehene Änderungen und wird beim Öffnen zurückgesetzt

- **Status:** Angenommen
- **Datum:** 2026-10-29
- **Auslöser:** Support-Anfrage — „die App zeigt seit 5 Tagen 2 offene Mitteilungen an, beim Öffnen
  sehe ich aber keine ungeöffneten Nachrichten“
- **Betrifft:** `:services` (Notifications), `iosApp/` (iOS und macOS), Android nur am Rand

## Kontext

### Was der Nutzer sieht

Auf dem App-Icon (iOS-Homescreen, macOS-Dock) steht eine rote **2**. Die App selbst hat aber keine
Liste von Mitteilungen, die „ungelesen“ sein könnten — der Stundenplan zeigt immer nur den aktuellen
Stand. Die Zahl lässt sich also weder in der App einsehen noch wegklicken. Sie bleibt, bis die
nächste Sammelbenachrichtigung sie überschreibt.

### Woher die Zahl kommt

`NotificationManager.notify()` unterscheidet zwei Fälle:

| Änderungen pro Prüfung | Aufruf | Badge auf iOS/macOS |
|---|---|---|
| 1 | `showNotification(…)` | wird **nicht** angefasst |
| ≥ 2 | `showSummaryNotification(…, changeCount)` | `setBadge(changeCount)` — absolut gesetzt |

Gesetzt wird die Badge in `IosNotificationDispatcher.showSummaryNotification` und
`MacosNotificationDispatcher.showSummaryNotification`. **Zurückgesetzt wird sie nirgends:** Im
ganzen Projekt gibt es keinen Aufruf von `setBadgeCount(0)`, weder beim Start, noch beim Wechsel in
den Vordergrund, noch in `cancelAllDelivered()` beim Logout.

Auf iOS und macOS ist die Badge eine Eigenschaft der App, nicht der Benachrichtigung. Wischt man
die Benachrichtigung in der Mitteilungszentrale weg oder tippt man sie an, bleibt die Zahl stehen.
Genau das beschreibt die Anfrage: eine Prüfung hat zwei Änderungen gefunden, die Badge steht seitdem
auf 2.

Daneben gibt es zwei Ungereimtheiten:

1. **Inkonsistent:** Eine einzelne Änderung erhöht die Badge nicht, zwei Änderungen setzen sie auf
   2, drei weitere in der nächsten Stunde setzen sie auf 3 statt 5. Die Zahl bedeutet „Größe der
   letzten Sammelbenachrichtigung“, und das ist für niemanden eine nützliche Information.
2. **Logout:** `cancelAllDelivered()` entfernt die Benachrichtigungen des vorigen Nutzers, die Badge
   bleibt. Sie ist genauso dessen Daten wie die Benachrichtigung selbst.

Android ist nicht betroffen: `setNumber(changeCount)` hängt an der Benachrichtigung, und der
Launcher-Punkt verschwindet mit ihr.

## Entscheidung

**Die Badge zeigt die Zahl der Stundenplan-Änderungen, die seit dem letzten Öffnen der App gemeldet
wurden. Sobald die App in den Vordergrund kommt, ist sie 0.**

„In den Vordergrund kommen“ gilt als „gesehen“, weil der Stundenplan beim Öffnen genau den Stand
zeigt, über den die Benachrichtigungen berichtet haben. Eine eigene Gelesen-Markierung pro Änderung
gäbe es nur mit einem Posteingang, und den gibt es nicht (siehe Alternative C).

Umsetzung:

1. **Zähler in `PlatformSettings`**, Schlüssel `unseen_lecture_changes` (Int). Es ist eine
   Einstellung, kein Geheimnis — also nicht in `SecureStorageInterface`
   (siehe „Two storages, on purpose“ in `AGENTS.md`).
2. **`NotificationManager.notify()` zählt hoch** — für *jede* gemeldete Änderung, auch die einzelne,
   und übergibt die Summe an den Dispatcher. Die Regel liegt damit einmal in `commonMain` und nicht in
   vier Dispatchern, und sie ist mit den vorhandenen Fakes in `NotificationManagerTest` testbar.
3. **`NotificationDispatcher` bekommt `setBadge(count: Int)`** mit leerer Default-Implementierung, wie
   `cancelAllDelivered()`. iOS und macOS rufen `UNUserNotificationCenter.setBadgeCount(count)` auf
   (iOS 16+/macOS 13+, das Deployment-Target ist 26). `showSummaryNotification` setzt keine Badge mehr
   über `content.badge`; Android behält `setNumber`.
4. **Zurücksetzen:** `NotificationManager.markChangesSeen()` setzt den Zähler auf 0 und ruft
   `setBadge(0)`. Aufgerufen von
   - `AppModel` (Swift) bei `scenePhase == .active` — deckt iOS und macOS ab, auch das Öffnen über den
     Menüleisten-Eintrag im Hintergrundmodus;
   - `MainActivity.onResume()` auf Android, damit der Zähler dort nicht unbegrenzt wächst;
   - dem Logout-Pfad, zusammen mit `cancelAllDelivered()`.
5. **Bestehende Installationen:** Der erste Start nach dem Update kommt in den Vordergrund und setzt
   die hängende Badge auf 0. Damit ist der gemeldete Fall ohne weiteres Zutun behoben.

### Prozessgrenze iOS

Die Hintergrundprüfung (`BGTaskScheduler`) läuft im App-Prozess, der Prozess der Widget-Extension
benachrichtigt nicht. `standardUserDefaults` reicht deshalb für den Zähler; die App Group wird nicht
gebraucht.

## Betrachtete Alternativen

### A — Badge ganz abschaffen

`setBadge` entfernen, `.badge` nicht mehr anfragen. Einfachste Lösung und behebt den Fehler.
**Verworfen**, weil die Badge für den Hauptzweck der Hintergrundprüfung — „im Stundenplan hat sich
etwas geändert“ — das unaufdringlichste Signal ist, das nach dem Wegwischen der Benachrichtigung
übrig bleibt. Bleibt die Rückfalloption, falls B sich nicht bewährt.

### B — Nur zurücksetzen, Bedeutung unverändert

`setBadgeCount(0)` beim Vordergrund, sonst nichts. Behebt das Symptom mit drei Zeilen Swift.
**Verworfen**, weil die Zahl weiterhin „Größe der letzten Sammelbenachrichtigung“ bedeutet und eine
einzelne Änderung gar nicht zählt. Der Unterschied zur Entscheidung ist ein Int in den Settings.

### C — Posteingang „Änderungen“ in der App

Erkannte `LectureChange`s in Room speichern, eigener Screen mit Gelesen-Status, Badge = ungelesene
Einträge. Das ist das, was die Formulierung „ungeöffnete Nachrichten“ eigentlich erwartet.
**Zurückgestellt**, nicht verworfen: neue Tabelle (Schema 5 mit Migration und Export), neuer Store,
neuer Screen auf zwei UI-Systemen, Aufbewahrungsregel. Die Entscheidung oben ist damit verträglich —
C würde nur die Quelle des Zählers und den Zeitpunkt des Zurücksetzens ändern. Eigene ADR, wenn
gewünscht.

### D — Zurücksetzen erst beim Öffnen des Stundenplans

Genauer als „App im Vordergrund“, aber der Stundenplan ist ohnehin der Start-Tab; der Unterschied
träfe nur Deep Links auf Noten oder Einstellungen. Nicht den zusätzlichen Kopplungspunkt wert.

## Konsequenzen

**Positiv**
- Der gemeldete Fehler verschwindet beim ersten Öffnen nach dem Update.
- Die Zahl hat eine Bedeutung, die man erklären kann, und ist über Prüfungen hinweg kumulativ.
- Logout nimmt die Badge mit.
- Die Logik liegt in `commonMain` und ist ohne Gerät testbar.

**Negativ / Risiken**
- Wer die App öffnet, ohne die geänderte Woche anzusehen, verliert das Signal. Akzeptiert;
  Alternative C wäre die Antwort darauf.
- Ein neuer Aufrufpunkt pro Plattform (`AppModel`, `MainActivity`), der vergessen werden kann.
  Abzusichern über einen Test in `NotificationManagerTest` für den Zähler und einen manuellen
  Prüfpunkt im Release-Check.

**Tests**
- `NotificationManagerTest`: eine Änderung → Badge 1; zwei Prüfungen mit 2 und 3 → Badge 5;
  `markChangesSeen()` → 0 und `setBadge(0)`; Logout → 0.
- `FakeNotificationDispatcher` zeichnet `setBadge`-Aufrufe auf.

## Antwort an den Nutzer (bis zum Update)

Umgehung bis zur Auslieferung: *Einstellungen → Mitteilungen → DHBW Horb → Kennzeichen* aus- und
wieder einschalten, oder dauerhaft aus. Es gibt keine verpassten Nachrichten in der App; die Zahl
stammt von einer Stundenplan-Benachrichtigung, die schon angezeigt wurde.
