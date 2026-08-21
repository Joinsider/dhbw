# Handoff — v3-Umbau

> Stand: 2026-08-21 · Branch `v3` bei `cb85c59` · Phasen P-1, P0, P1, P2 abgeschlossen
> Nächste Phase: **P3 — Domain, Repositories, Fehlermodell**

---

## 1. Zuerst lesen

| Datei | Was drinsteht |
|---|---|
| `.planning/v3-ARCHITECTURE-PLAN.md` | **Der Plan.** Zielarchitektur (§1), alle Phasen P-1…P9 mit Abnahmekriterien (§2), Querschnitt und offene Punkte (§3). Abgeschlossene Phasen sind als solche markiert und enthalten, was tatsächlich passiert ist — inklusive der Abweichungen vom ursprünglichen Plan. |
| `AGENTS.md` | Ist nach jeder Phase aktualisiert und beschreibt den **aktuellen** Stand: Modulgraph, DI-Regeln, Build-Befehle. Wenn Plan und AGENTS.md sich widersprechen, hat AGENTS.md recht. |
| dieses Dokument | Umgebung, Arbeitsweise, Fallstricke, konkreter nächster Schritt. |

Die Entscheidungen des Nutzers stehen im Plan-Kopf: Architektur **B (Shared Store / MVI-UDF)**,
inkrementelle Migration, iOS vollumfänglich nativ inkl. Widget & Notifications, mit Koin, SKIE,
Navigation-Library und echten Room-Migrationen.

---

## 2. Arbeitsweise, die der Nutzer festgelegt hat

* **Nach jeder Phase anhalten.** Phase bauen, Diff und Gate-Ergebnis zeigen, auf Freigabe warten.
  Nicht mehrere Phasen am Stück durchziehen.
* **Nichts pushen.** Alles bleibt lokal. Keine PRs, kein `git push`.
* Branch-Modell: Integration auf `v3`, je Phase ein `phase/pN-…`-Branch, gemerged mit `--no-ff`.
  (Git erlaubt `v3` und `v3/…` nicht gleichzeitig als Ref — daher das `phase/`-Präfix.)
* Der Nutzer schreibt Deutsch. Code, Kommentare, Commit-Messages und Testnamen sind Englisch.

```bash
git checkout -b phase/p3-domain v3
# … arbeiten, Gate grün bekommen …
git commit
git checkout v3 && git merge --no-ff phase/p3-domain -m "merge: P3 … into v3"
```

---

## 3. Umgebung

**`ANDROID_HOME` ist nicht gesetzt und es gibt keine `local.properties`.** Ohne das schlägt jeder
Android-Task mit „SDK location not found" fehl. Jedem Gradle-Aufruf voranstellen:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :composeApp:testDebugUnitTest :composeApp:desktopTest
```

Vorhanden: Java 25 (Zulu), Xcode 26.6, Android SDK unter `~/Library/Android/sdk`,
Simulator „iPhone 17" (`8D76539B-73DF-410F-B550-027260B1BAAD`), ein laufender Android-Emulator
(`emulator-5554`, Pixel 9 Pro).

**Fallstricke, die mich Zeit gekostet haben:**

* `timeout` gibt es auf dieser Shell nicht (kein coreutils). Stattdessen Hintergrundprozess plus
  `sleep`, oder das Task-Timeout des Bash-Tools nutzen.
* Das Arbeitsverzeichnis wandert zwischen Bash-Aufrufen. Nach jedem Fehlschlag prüfen, wo man ist —
  ich habe einmal eine Datei nach `iosApp/composeApp/…` geschrieben, weil das cwd nicht war, was ich
  dachte. Im Zweifel absolute Pfade.
* Die JVM läuft auf **Locale de_DE**. UI-Tests dürfen deshalb nie auf sichtbaren Text prüfen, der
  aus einer String-Ressource kommt — sie wären auf dem englischen CI-Runner grün und hier rot.
  Stabile Test-Tags benutzen (`navItemTestTag`, `themeButtonTestTag`).
* Gradle nutzt Configuration Cache; „BUILD SUCCESSFUL in 1s" heißt oft, dass nichts lief.
  Zum echten Nachweis `--rerun-tasks` und danach die Testzahlen aus den XMLs zählen.

---

## 4. Verifikation — das Gate

Eine Phase ist erst fertig, wenn beides grün ist:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :composeApp:testDebugUnitTest :composeApp:desktopTest --rerun-tasks
```

**Sollwerte bei `cb85c59`:** `testDebugUnitTest` 211 Tests, `desktopTest` 326 Tests, 0 Fehler,
1 bzw. 5 dokumentiert übersprungen. Coverage 37,5 %.

Zahlen zählen (Gradle meldet sie nicht immer):

```bash
python3 - <<'EOF'
import glob, re
for d in ["testDebugUnitTest", "desktopTest"]:
    t = f = sk = 0
    for x in glob.glob(f"composeApp/build/test-results/{d}/*.xml"):
        s = open(x, encoding="utf-8", errors="replace").read()
        m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', s)
        if m:
            t += int(m.group(1)); sk += int(m.group(2)); f += int(m.group(3)) + int(m.group(4))
    print(f"{d:22s} tests={t:4d} failures={f:3d} skipped={sk}")
EOF
```

**Zusätzlich pro Phase:**

```bash
# Das Framework muss Compose-frei bleiben — Ergebnis muss 0 sein
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
nm -gU shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Shared | grep -c androidx.compose

# iOS-Build (braucht keine Signatur)
cd iosApp && ANDROID_HOME=$HOME/Library/Android/sdk xcodebuild -project iosApp.xcodeproj \
  -scheme iosApp -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

**Wichtigste Lektion aus P2: grüner Build ≠ funktionierende App.** Eine fehlende Koin-Bindung ist
kein Compile-Fehler, und der iOS-Start-Reihenfolgen-Bug hat alle Tests bestanden und ist trotzdem
beim ersten Frame abgestürzt. Nach jeder Phase, die Verdrahtung oder Lebenszyklus anfasst, beide
Apps wirklich starten:

```bash
# Android
ADB=$HOME/Library/Android/sdk/platform-tools/adb
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :composeApp:installDebug
$ADB shell am force-stop de.fampopprol.dhbwhorb
$ADB logcat -c && $ADB shell am start -n de.fampopprol.dhbwhorb/de.fampopprol.dhbwhorb.MainActivity
sleep 10 && $ADB logcat -d --pid=$($ADB shell pidof de.fampopprol.dhbwhorb) | grep -iE "koin|fatal|exception"

# iOS — Achtung: Bundle-ID ist de.fampopprol.dhbw (ohne "horb"), der Android-Package heißt anders
cd iosApp && xcodebuild … -derivedDataPath /tmp/dhbwderived build
xcrun simctl install <UDID> /tmp/dhbwderived/Build/Products/Debug-iphonesimulator/DHBW-Horb.app
xcrun simctl launch --console-pty <UDID> de.fampopprol.dhbw
```

Screenshots und Eingaben gehen über `mcp__Claude_Code_iOS_Simulator__control`.
**Texteingabe im Simulator ist unbrauchbar für Sonderzeichen** — der Simulator mappt auf die
deutsche Mac-Tastatur, aus `@` wird `"` und aus `-` ein `ß`. Der Demo-Login
(`demo@hb.dhbw-stuttgart.de` / `demo123`) lässt sich so nicht eintippen. Wer die Screens hinter dem
Login auf dem Gerät braucht, muss einen anderen Weg finden; sonst über Tests abdecken.

---

## 5. Wo was liegt

```
:core:common     Platform-Erkennung, appCoroutineScope, coreModule
:domain          Dualis-Modelle, TimeHelper                      ← keine Frameworks
:data            Ktor, Parser, Dualis-Services, Room + DAOs, SecureStorage, Prefs, dataModule
:services        LectureService, LogoutUseCase, Notifications, Widget-UseCases, FileViewer
:presentation    ViewModels (noch mit Compose-Runtime, siehe unten)
:shared          initKoin() + Umbrella → Shared.framework für Apple
:composeApp      Compose-UI, Entry Points, Glance-Widget
```

Der Package-Root ist in **jedem** Modul `de.fampopprol.dhbwhorb` — Modulgrenzen ändern keine
Package-Namen. Verschieben zwischen Modulen erfordert deshalb keine Import-Anpassung.

Wichtige Einstiegspunkte:
* `shared/…/shared/Koin.kt` — `initKoin()`, der eine Composition Root
* `data/…/data/di/DataModule.kt` + `DataPlatformModule.kt` (expect/actual je Plattform)
* `services/…/services/di/ServicesModule.kt` + `ServicesPlatformModule.kt`
* `presentation/…/presentation/di/PresentationModule.kt`
* `composeApp/…/testutil/TestKoin.kt` — `WithTestKoin { }` für Compose-Tests, `testKoin()` sonst
* `composeApp/src/desktopTest/…/di/KoinGraphTest.kt` — Graph-Prüfung, bei neuen Bindungen mitpflegen

---

## 6. Bekannte, bewusst offene Punkte

Alle sind im Code kommentiert und im Plan vermerkt — nichts davon ist vergessen worden.

| Punkt | Ort | Fällig |
|---|---|---|
| `extractWeekDates()` nimmt das Jahr aus `Clock.System.now()`. Der Pager erlaubt ±1000 Wochen, also bekommt jede Woche außerhalb des laufenden Jahres das falsche Datum. Korrektur braucht den angefragten Zeitraum, den der Parser nicht kennt. | `@Ignore`-Test in `TimetableParserTest.kt:138` | **P3** |
| `SessionManager.isReAuthenticating` ist ein ungeschütztes Boolean, an drei Stellen abgefragt. Parallele 401-Antworten lösen mehrere Re-Logins aus. | `AuthenticationService`, `DualisGradeService`, `DualisDocumentService` | **P3** |
| 69 × `catch (e: Exception)` (data 30, services 22, presentation 10, composeApp 7), nur 4 Dateien nutzen `Result`. Die UI kann offline / Session abgelaufen / Parse-Fehler nicht unterscheiden. | überall | **P3** |
| `:presentation` ist **nicht** in `Shared.framework`, weil die ViewModels über `mutableStateOf` an der Compose-Runtime hängen (ein Versuch ergab 19.504 Compose-Symbole). | `PresentationModule.kt`, `shared/build.gradle.kts` | **P4** |
| `TimetablePageTest` — 4 Tests `@Ignore`t, brauchen injizierbaren State. | `TimetablePageTest.kt` | **P4** |
| `useMaterialYou` ist auf Desktop wirkungslos: `Theme.desktop.kt` ignoriert das Flag und erzeugt immer ein Seed-Schema. Die Einstellung existiert in der UI, tut dort aber nichts. | `Theme.desktop.kt` | **P5** |
| `fallbackToDestructiveMigration(dropAllTables = true)` in allen vier `DatabaseFactory.*.kt` — jedes Schema-Update löscht Nutzerdaten. Dazu: iOS-DB liegt in `NSDocumentDirectory`, wo die Widget-Extension nicht lesen kann. | `data/src/*/…/DatabaseFactory.*.kt` | **P6** |
| Tests liegen noch alle in `:composeApp`, nicht in ihren Modulen. Bewusst so: die Gates bleiben unverändert, und der Umzug braucht pro Modul eigene Test-Abhängigkeiten. | `composeApp/src/commonTest` | P3/P4 |
| `services/notifications/IntegrationExample.kt` — 213 Zeilen Beispielcode mit eigenem `CoroutineScope`, wird von nichts benutzt. | `:services` | P9 |

Der iOS-`LectureMonitorScheduler` ist weiterhin ein reiner Log-Stub — auf iOS gibt es **kein**
Background-Monitoring, obwohl die Einstellung es anbietet. Das ist eine Feature-Lücke aus der Zeit
vor dem Umbau, keine Regression, und in P8 eingeplant.

---

## 7. Nächster Schritt: P3

Der Plan beschreibt P3 in §2. Kurzfassung der Reihenfolge, die ich wählen würde:

1. `Outcome<T>` / `AppError` in `:core:common` (Definition steht in §1.4 des Plans). Eigene Sealed
   Hierarchie statt `kotlin.Result`, weil SKIE daraus in P7 echte Swift-Enums macht.
2. Repository-Interfaces in `:domain`, Implementierungen in `:data` — sie kapseln die vorhandenen
   `DualisXService`-Klassen plus DAO-Zugriff.
3. `LectureService` aufteilen: Cache-First-Orchestrierung → `TimetableRepositoryImpl`,
   Widget-Zugriff → eigener UseCase. Die Doppelrolle „Service ist gleichzeitig
   `WidgetLectureRepository`" entfällt.
4. UseCases mit je einer Aufgabe (Liste im Plan).
5. Jeden `catch (e: Exception)` in ein konkretes `AppError` überführen. **Nicht pauschal** — bei
   jedem einzeln entscheiden, welcher Fall es ist. Das ist der eigentliche Wert der Phase.
6. Re-Auth-Single-Flight mit `Mutex` + `Deferred`-Dedupe.
7. Den Jahres-Defekt aus P0 beheben und den `@Ignore` entfernen.

Beim Anfassen der Fehlerbehandlung gilt: wenn ein bestehender Test eine Erwartung hat, die dem
neuen Verhalten widerspricht, erst prüfen **wer recht hat**. In P-1 hatten zwei Tests unrecht
(gültige DHBW-Adresse als Negativbeispiel, Desktop-Farbschema) und einer recht (Logout-Button im
ausgeloggten Zustand sichtbar — das war ein echter Bug).

---

## 8. Zur Parallelisierung

Der Nutzer hat nach Subagents und Worktrees gefragt. Meine Einschätzung nach drei Phasen:

* **Über Phasen hinweg lohnt es sich nicht.** Die Kette ist bis P4 linear, und jede Phase schreibt
  dieselben zentralen Dateien um.
* **Innerhalb einer Phase über disjunkte Dateimengen schon.** P3 (5 Repositories), P4 (6 Stores),
  P7 (5 SwiftUI-Screens) sind echte Fan-outs — jeder Agent schreibt eigene, neue Dateien.
* Voraussetzung ist, dass die gemeinsamen Verträge **vorher** stehen: für P3 also `Outcome`/`AppError`
  und die Repository-Interfaces, bevor die Implementierungen verteilt werden. Sonst erfindet jeder
  Agent seine eigene Fehlerbehandlung.
