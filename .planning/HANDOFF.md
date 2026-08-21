# Handoff — v3-Umbau

> Stand: 2026-08-21 · Branch `phase/p5-navigation` · Phasen P-1 … P5 abgeschlossen
> Nächste Phase: **P6 — Room-Migrationen** (danach P7, das P6 voraussetzt)

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
git checkout -b phase/p6-migrations v3
# … arbeiten, Gate grün bekommen …
git commit
git checkout v3 && git merge --no-ff phase/p6-migrations -m "merge: P6 … into v3"
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

**Sollwerte nach P5:** `testDebugUnitTest` 288 Tests, `desktopTest` 386 Tests, 0 Fehler,
**0 übersprungen**. Es gibt keinen `@Ignore` mehr im Projekt — wenn einer auftaucht, gehört ein
Grund und eine Phase dazu.

Neue Compose-UI-Tests im Wurzelpaket brauchen einen Eintrag in der Ausschlussliste in
`composeApp/build.gradle.kts` (`**/…Test.class`), sonst laufen sie auch als Android-Unit-Test und
scheitern dort — der bestehende Filter deckt nur `**/ui/**` ab.

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
beim ersten Frame abgestürzt.

**P4 hat das bestätigt und verschärft: starten reicht nicht, man muss durchklicken.** Zwei Funde,
die ein grünes Gate und ein erfolgreicher Start beide durchgelassen haben — der Absturz beim
Öffnen der Einstellungen (seit P2!), und das Nachladen bei jedem Tab-Wechsel. Also nach jeder
Phase **jeden Screen** einmal öffnen, und beim Stundenplan/Noten/Dokumente-Durchlauf zusätzlich
`adb logcat -d --pid=… | grep -c "Executing GET request"` prüfen.

Beide Apps wirklich starten:

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
:core:common     Outcome/AppError, Platform-Erkennung, appCoroutineScope, coreModule
:domain          Dualis- und Domänenmodelle, Repository-Interfaces, UseCases, TimeHelper
                 ← keine Frameworks
:data            Ktor, Parser, Dualis-Services, Gateway, ReAuthenticator, Room + DAOs,
                 SecureStorage, Prefs, Repository-Impls, dataModule
:services        Notifications, Widget-UseCases, FileViewer
:presentation    MVI-Stores ← keine Compose-Runtime, im Shared.framework enthalten
:shared          initKoin() + Umbrella → Shared.framework für Apple
:composeApp      Compose-UI, Entry Points, Glance-Widget
```

**Alles oberhalb von `:data` spricht nur mit den Repository-Interfaces und UseCases aus
`:domain`.** Die `DualisXService`-Klassen sind `:data`-Interna; wer außerhalb von `:data` einen
davon aus Koin holt, umgeht die Schicht.

Der Package-Root ist in **jedem** Modul `de.fampopprol.dhbwhorb` — Modulgrenzen ändern keine
Package-Namen. Verschieben zwischen Modulen erfordert deshalb keine Import-Anpassung.

Wichtige Einstiegspunkte:
* `shared/…/shared/Koin.kt` — `initKoin()`, der eine Composition Root
* `data/…/data/di/DataModule.kt` + `DataPlatformModule.kt` (expect/actual je Plattform)
* `services/…/services/di/ServicesModule.kt` + `ServicesPlatformModule.kt`
* `presentation/…/presentation/di/PresentationModule.kt`
* `presentation/…/presentation/store/` — `Store`, `BaseStore`, `EffectScope`
* `presentation/…/presentation/<feature>/` — je ein Contract (State/Intent/Msg/Effect) und Store
* `composeApp/…/ui/store/StoreCompose.kt` — `collectState()` und `HandleEffects { }`
* `composeApp/…/ui/navigation/Routes.kt` + `DhbwNavHost.kt` — typisierte Routen, Graph, Deep Links
* `data/…/data/repository/` — die sechs Repository-Implementierungen
* `data/…/dualis/remote/services/DualisPageGateway.kt` — jeder authentifizierte Seitenabruf
* `data/…/dualis/remote/session/ReAuthenticator.kt` — Single-Flight-Re-Login
* `core/common/…/core/error/` — `Outcome` und `AppError`
* `composeApp/…/ui/error/AppErrorMessage.kt` — die einzige Stelle, an der ein Fehler Text wird
* `composeApp/…/testutil/TestKoin.kt` — `WithTestKoin { }` für Compose-Tests, `testKoin()` sonst
* `composeApp/src/desktopTest/…/di/KoinGraphTest.kt` — Graph-Prüfung, bei neuen Bindungen mitpflegen

---

## 6. Bekannte, bewusst offene Punkte

Alle sind im Code kommentiert und im Plan vermerkt — nichts davon ist vergessen worden.

| Punkt | Ort | Fällig |
|---|---|---|
| `fallbackToDestructiveMigration(dropAllTables = true)` in allen vier `DatabaseFactory.*.kt` — jedes Schema-Update löscht Nutzerdaten. Dazu: iOS-DB liegt in `NSDocumentDirectory`, wo die Widget-Extension nicht lesen kann. | `data/src/*/…/DatabaseFactory.*.kt` | **P6** |
| Die Stores sind Applikations-Singles, nicht im Navigations-Scope. Bewusst so: ein Halter je Navigationseintrag würde bei jedem Tab-Wechsel neu laden — genau das, was P4 abgestellt hat. Deshalb bleibt auch `EnsureLoaded` bestehen. | `PresentationModule.kt`, `GradesStore`, `DocumentsStore` | — |
| Wenn die Sitzung abläuft, während man eingeloggt im Graphen steht, zeigt die Seite „bitte anmelden" statt zur Login-Wurzel zurückzukehren. Ehrlich, aber nicht schön. | `GradesPage`, `DocumentsPage` | P9 |
| Die Fakes für die sechs Repositories liegen in `composeApp/commonTest/testutil/fakes/`, nicht in einem `:core:testing`. Bewusst so, solange alle Tests in `:composeApp` liegen — beides zusammen umziehen. | `testutil/fakes/` | P9 |
| Tests liegen noch alle in `:composeApp`, nicht in ihren Modulen. Die Gates bleiben dadurch unverändert; der Umzug braucht pro Modul eigene Test-Abhängigkeiten. | `composeApp/src/commonTest` | P9 |
| `NotificationDispatcher` hält seinen Android-Context statisch, weil `expect class` keinen plattformspezifischen Konstruktorparameter erlaubt. Wie bei `SecureStorage` in P2 ist die Lösung ein Interface mit je einer Implementierung pro Plattform. | `NotificationDispatcher.android.kt` | P8/P9 |
| Der Widget-UseCase liefert bei einem Lesefehler des Caches eine leere Liste statt eines Fehlers — ein Widget hat keine Fehlerdarstellung. Bewusst so, im Code begründet. | `WidgetTimetableUseCase.kt` | — |
| 48 × `catch (e: Exception)` übrig (von 69). Drei Sorten, alle bewusst: Parser (schlucken eine Zeile, nicht die Seite), die Klassifikationsstellen selbst, und die Plattformschicht. Im Dualis-Datenpfad ist keines mehr. | `:data`, `:services`, `:composeApp` | — |

Der iOS-`LectureMonitorScheduler` ist weiterhin ein reiner Log-Stub — auf iOS gibt es **kein**
Background-Monitoring, obwohl die Einstellung es anbietet. Das ist eine Feature-Lücke aus der Zeit
vor dem Umbau, keine Regression, und in P8 eingeplant.

---

## 7. Nächster Schritt: P6

**Vor P6 zu beantworten** (offener Punkt 2 in §3): Soll der iOS-DB-Umzug die vorhandenen Daten
kopieren oder einmalig neu synchronisieren? Kopieren ist sauberer, Neu-Sync ist deutlich weniger
Code — und da die Datenbank ohnehin nur ein Cache von Dualis ist, spricht einiges für Neu-Sync.
Das ist eine Nutzerentscheidung, keine technische.

Reihenfolge, die ich wählen würde:

1. `fallbackToDestructiveMigration` raus, echte `Migration`-Objekte rein. Die Schemas liegen
   exportiert in `data/schemas/` — Schema 4 ist der Ausgangspunkt.
2. Migrationstests: von Schema 4 auf 5 mit echten Daten, und die Kette von 1 an, falls es
   Installationen mit älteren Schemas gibt.
3. Die iOS-Datenbank aus `NSDocumentDirectory` in die App-Group verschieben, damit die
   Widget-Extension sie lesen kann. **Achtung:** die App-Group muss dafür im Apple-Developer-Portal
   eingerichtet sein (offener Punkt 4 in §3, eigentlich für P8 notiert — für P6 wird sie schon
   gebraucht).

P6 ist Voraussetzung für P7 (natives SwiftUI), weil das Widget dort auf dieselbe Datenbank
zugreift.

## 8. Zur Parallelisierung

Der Nutzer hat nach Subagents und Worktrees gefragt. Meine Einschätzung nach drei Phasen:

* **Über Phasen hinweg lohnt es sich nicht.** Die Kette ist bis P4 linear, und jede Phase schreibt
  dieselben zentralen Dateien um. P3 hat das bestätigt: fast jede Datei im Datenpfad wurde
  angefasst.
* **Innerhalb einer Phase über disjunkte Dateimengen schon.** P3 (5 Repositories), P4 (6 Stores),
  P7 (5 SwiftUI-Screens) sind echte Fan-outs — jeder Agent schreibt eigene, neue Dateien.
* Voraussetzung ist, dass die gemeinsamen Verträge **vorher** stehen. In P3 waren das
  `Outcome`/`AppError` plus die Repository-Interfaces, in P4 `Store`/`BaseStore`/`EffectScope` —
  beide Male zuerst geschrieben, beide Male hat es getragen. Für P7 (fünf SwiftUI-Screens) wäre es
  entsprechend die Swift-seitige Store-Anbindung.
