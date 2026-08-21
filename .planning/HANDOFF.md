# Handoff — v3-Umbau

> Stand: 2026-08-21 · Phasen P-1, P0, P1, P2, P3, P4, P5 abgeschlossen
> `v3` steht bei `1330f4c` (P4 gemerged) · **P5 liegt fertig auf `phase/p5-navigation` (`0d9793f`) und ist noch nicht gemerged**
> Nächste Phase: **P6 — Room-Migrationen**

---

## 0. Das Allererste

P5 ist fertig, geprüft und committet, aber noch nicht auf `v3`. Bevor irgendetwas anderes passiert:

```bash
git checkout v3 && git merge --no-ff phase/p5-navigation -m "merge: P5 navigation into v3"
```

Danach ist `v3` der aktuelle Stand und P6 kann von dort abzweigen.

---

## 1. Zuerst lesen

| Datei | Was drinsteht |
|---|---|
| `.planning/v3-ARCHITECTURE-PLAN.md` | **Der Plan.** Zielarchitektur (§1), alle Phasen P-1…P9 mit Abnahmekriterien (§2), Querschnitt und offene Punkte (§3). Abgeschlossene Phasen sind markiert und enthalten unter „Was tatsächlich passiert ist" die Abweichungen vom Plan — die sind oft interessanter als der Plan selbst. |
| `AGENTS.md` | Beschreibt den **aktuellen** Stand: Modulgraph, Fehlermodell, Store-Vertrag, Navigation, DI-Regeln, Build-Befehle. Wenn Plan und AGENTS.md sich widersprechen, hat AGENTS.md recht. |
| dieses Dokument | Umgebung, Arbeitsweise, Fallstricke, konkreter nächster Schritt. |

Die Grundentscheidungen des Nutzers stehen im Plan-Kopf: Architektur **B (Shared Store / MVI-UDF)**,
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
# anhalten, berichten, auf Freigabe warten
git checkout v3 && git merge --no-ff phase/p6-migrations -m "merge: P6 … into v3"
```

Commit-Messages: erklären **warum**, nicht was. Die bisherigen Phasen-Commits sind das Muster —
sie benennen auch, was schiefging und was bewusst nicht gemacht wurde.

---

## 3. Umgebung

**`ANDROID_HOME` ist nicht gesetzt und es gibt keine `local.properties`.** Ohne das schlägt jeder
Android-Task mit „SDK location not found" fehl. Jedem Gradle-Aufruf voranstellen:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :composeApp:testDebugUnitTest :composeApp:desktopTest
```

Vorhanden: Java 25 (Zulu), Xcode 26.6, Android SDK unter `~/Library/Android/sdk`,
iOS-Simulator „iPhone 17" (`8D76539B-73DF-410F-B550-027260B1BAAD`), Android-AVD `Pixel_9_Pro`.

**Der Android-Emulator läuft nicht zwangsläufig.** Er stirbt zwischen Sitzungen. Starten und warten:

```bash
nohup $HOME/Library/Android/sdk/emulator/emulator -avd Pixel_9_Pro -no-snapshot-save >/tmp/emu.log 2>&1 &
ADB=$HOME/Library/Android/sdk/platform-tools/adb
$ADB wait-for-device
until [ "$($ADB shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done
```

**Fallstricke, die Zeit gekostet haben:**

* `timeout` gibt es auf dieser Shell nicht (kein coreutils). Hintergrundprozess plus `sleep`, oder
  das Task-Timeout des Bash-Tools.
* **Das Arbeitsverzeichnis wandert zwischen Bash-Aufrufen.** Nach jedem „no such file"-Fehler
  prüfen, wo man ist. Im Zweifel absolute Pfade oder ein `cd` an den Anfang der Zeile.
* Die JVM läuft auf **Locale de_DE**. UI-Tests dürfen nie auf sichtbaren Text prüfen, der aus einer
  String-Ressource kommt — sie wären auf dem englischen CI-Runner grün und hier rot. Stabile
  Test-Tags benutzen (`navItemTestTag(item)`, `themeLightButton`, `gradesPageTitle`).
* Gradle nutzt Configuration Cache; „BUILD SUCCESSFUL in 1s" heißt oft, dass nichts lief. Zum
  echten Nachweis `--rerun-tasks` und danach die Testzahlen aus den XMLs zählen.
* Auf dem iOS-Simulator ist die **Bundle-ID `de.fampopprol.dhbw`** (ohne „horb"), der
  Android-Package heißt `de.fampopprol.dhbwhorb`.
* **Texteingabe im iOS-Simulator ist für Sonderzeichen unbrauchbar** — er mappt auf die deutsche
  Mac-Tastatur, aus `@` wird `"`. Der Demo-Login (`demo@hb.dhbw-stuttgart.de` / `demo123`) lässt
  sich so nicht eintippen. Screens hinter dem Login dort über Tests abdecken.
* Auf dem **Android-Emulator ist eine echte Sitzung gespeichert.** Die App startet dort eingeloggt
  und mit echten Daten — praktisch zum Durchklicken, aber Vorsicht: das sind reale Dualis-Requests.

---

## 4. Verifikation — das Gate

Eine Phase ist erst fertig, wenn alles Folgende grün ist.

### 4.1 Tests

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew \
  :composeApp:testDebugUnitTest :composeApp:desktopTest --rerun-tasks
```

**Sollwerte nach P5:** `testDebugUnitTest` **288**, `desktopTest` **386**, 0 Fehler,
**0 übersprungen**. Es gibt seit P4 keinen einzigen `@Ignore` mehr im Projekt — wenn einer
auftaucht, gehören ein Grund und eine Phase dazu.

Gradle meldet die Zahlen nicht zuverlässig, also selbst zählen:

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

Fehlermeldungen stehen nicht in der Konsolenausgabe, sondern in den XMLs:

```bash
python3 - <<'EOF'
import glob, re, html
for x in glob.glob("composeApp/build/test-results/desktopTest/*.xml"):
    s = open(x, encoding="utf-8", errors="replace").read()
    for m in re.finditer(r'<testcase name="([^"]+)"[^>]*>\s*<failure[^>]*message="([^"]*)"', s):
        print(m.group(1), "->", html.unescape(m.group(2))[:300])
EOF
```

**Neue Compose-UI-Tests im Wurzelpaket** brauchen einen Eintrag in der Ausschlussliste in
`composeApp/build.gradle.kts` — der bestehende Filter deckt nur `**/ui/**/*Test.class` ab, alles
darüber läuft sonst auch als Android-Unit-Test und scheitert dort.

### 4.2 Das Framework muss Compose-frei bleiben

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
nm -gU shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Shared \
  | grep -c androidx.compose        # muss 0 sein
```

Seit P4 ist `:presentation` Teil des Frameworks. Eine Compose-Abhängigkeit dort einzuschleppen
kompiliert problemlos und bricht erst P7 — diese Prüfung ist das, was es merkt.

### 4.3 iOS-Build (braucht keine Signatur)

```bash
cd iosApp && ANDROID_HOME=$HOME/Library/Android/sdk xcodebuild \
  -project iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/dhbwderived \
  CODE_SIGNING_ALLOWED=NO build
```

### 4.4 Die wichtigste Regel: **grüner Build ≠ funktionierende App, und Starten ≠ Durchklicken**

Das ist die teuerste Lektion des Umbaus, zweimal gelernt:

* **P2:** Eine fehlende Koin-Bindung ist kein Compile-Fehler. Der iOS-Start-Reihenfolgen-Bug hat
  alle Tests bestanden und ist trotzdem beim ersten Frame abgestürzt.
* **P4:** Zwei Funde, die grünes Gate *und* erfolgreichen Start beide passiert haben — ein Absturz
  beim Öffnen der Einstellungen (der seit P2 drin war und niemandem aufgefallen ist), und
  Nachladen bei jedem Tab-Wechsel. Beide nur sichtbar beim Durchklicken.

Also nach jeder Phase: **beide Apps starten und jeden Screen einmal öffnen.**

```bash
# Android
ADB=$HOME/Library/Android/sdk/platform-tools/adb
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :composeApp:installDebug
$ADB shell am force-stop de.fampopprol.dhbwhorb
$ADB logcat -c
$ADB shell am start -n de.fampopprol.dhbwhorb/de.fampopprol.dhbwhorb.MainActivity
sleep 14
PID=$($ADB shell pidof de.fampopprol.dhbwhorb | tr -d '\r')
$ADB logcat -d | grep -iE "FATAL|AndroidRuntime"        # muss leer sein
$ADB exec-out screencap -p > /tmp/screen.png            # und wirklich anschauen

# iOS
xcrun simctl bootstatus 8D76539B-73DF-410F-B550-027260B1BAAD -b
xcrun simctl install 8D76539B-73DF-410F-B550-027260B1BAAD \
  /tmp/dhbwderived/Build/Products/Debug-iphonesimulator/DHBW-Horb.app
xcrun simctl launch 8D76539B-73DF-410F-B550-027260B1BAAD de.fampopprol.dhbw
```

Screenshots und Eingaben auf iOS gehen über `mcp__Claude_Code_iOS_Simulator__control`, auf Android
über `adb exec-out screencap -p` und `adb shell input tap X Y`. Die Tab-Leiste liegt bei
y ≈ 2655; die vier Tabs bei x ≈ 150 / 479 / 802 / 1120.

**Zusätzlich seit P4 — nachmessen statt behaupten:** Beim Durchklicken durch Stundenplan → Noten →
Dokumente → Einstellungen darf **kein einziger Request** fliegen, wenn die Stores schon geladen sind:

```bash
$ADB logcat -c
# … Tabs durchklicken …
$ADB logcat -d --pid=$PID | grep -c "Executing GET request"    # muss 0 sein
```

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
:composeApp      Compose-UI, Navigation, Entry Points, Glance-Widget
```

**Alles oberhalb von `:data` spricht nur mit den sechs Repository-Interfaces und den UseCases aus
`:domain`.** Die `DualisXService`-Klassen sind `:data`-Interna; wer außerhalb von `:data` einen
davon aus Koin holt, umgeht die Schicht.

Der Package-Root ist in **jedem** Modul `de.fampopprol.dhbwhorb` — Modulgrenzen ändern keine
Package-Namen. Eine Datei zwischen Modulen zu verschieben erfordert deshalb keine Import-Anpassung.

**Wichtige Einstiegspunkte:**

| Was | Wo |
|---|---|
| Composition Root | `shared/…/shared/Koin.kt` — `initKoin()` |
| Koin-Module | `core/…/core/di/CoreModule.kt`, `data/…/data/di/DataModule.kt` (+ `DataPlatformModule.kt` je Plattform), `services/…/di/ServicesModule.kt`, `presentation/…/di/PresentationModule.kt` |
| Fehlermodell | `core/common/…/core/error/AppError.kt`, `Outcome.kt` |
| Fehler → Text | `composeApp/…/ui/error/AppErrorMessage.kt` (die **einzige** Stelle) |
| Repositories | `data/…/data/repository/` (sechs Implementierungen + `Mappers.kt`) |
| Authentifizierter Seitenabruf | `data/…/dualis/remote/services/DualisPageGateway.kt` |
| Single-Flight-Re-Login | `data/…/dualis/remote/session/ReAuthenticator.kt` |
| Store-Vertrag | `presentation/…/presentation/store/` (`Store`, `BaseStore`, `EffectScope`) |
| Die sechs Stores | `presentation/…/presentation/{app,auth,timetable,grades,documents,settings}/` |
| Store ↔ Compose | `composeApp/…/ui/store/StoreCompose.kt` — `collectState()`, `HandleEffects { }` |
| Navigation | `composeApp/…/ui/navigation/Routes.kt`, `DhbwNavHost.kt` |
| Test-Graph | `composeApp/src/commonTest/…/testutil/TestKoin.kt` — `WithTestKoin { }`, `testKoin()` |
| Repository-Fakes | `composeApp/src/commonTest/…/testutil/fakes/FakeRepositories.kt` |
| Store-Test-Helfer | `composeApp/src/commonTest/…/presentation/StoreTestSupport.kt` |
| Graph-Prüfung | `composeApp/src/desktopTest/…/di/KoinGraphTest.kt` — bei neuen Bindungen mitpflegen |

---

## 6. Bekannte, bewusst offene Punkte

Alle sind im Code kommentiert und im Plan vermerkt — nichts davon ist vergessen worden.

| Punkt | Ort | Fällig |
|---|---|---|
| `fallbackToDestructiveMigration(dropAllTables = true)` in allen vier `DatabaseFactory.*.kt` — jedes Schema-Update löscht Nutzerdaten. Dazu: die iOS-DB liegt in `NSDocumentDirectory`, wo die Widget-Extension nicht lesen kann. | `data/src/*/…/DatabaseFactory.*.kt` | **P6** |
| Die Stores sind Applikations-Singles, nicht im Navigations-Scope. Bewusst so: ein Halter je Navigationseintrag würde bei jedem Tab-Wechsel neu laden — genau das, was P4 abgestellt hat. Deshalb bleibt auch `EnsureLoaded` neben `Load` bestehen. | `PresentationModule.kt`, `GradesStore`, `DocumentsStore` | — |
| Läuft die Sitzung ab, während man eingeloggt im Graphen steht, zeigt die Seite „bitte anmelden" statt zur Login-Wurzel zurückzukehren. Ehrlich, aber nicht schön. | `GradesPage`, `DocumentsPage` | P9 |
| Der iOS-`LectureMonitorScheduler` ist ein reiner Log-Stub — auf iOS gibt es **kein** Background-Monitoring, obwohl die Einstellung es anbietet. Feature-Lücke von vor dem Umbau, keine Regression. | `LectureMonitorScheduler.ios.kt` | **P8** |
| `NotificationDispatcher` hält seinen Android-Context statisch, weil `expect class` keinen plattformspezifischen Konstruktorparameter erlaubt. Wie bei `SecureStorage` in P2 ist die Lösung ein Interface mit je einer Implementierung pro Plattform. Bis dahin: `DualisApplication.onCreate()` **muss** `initialize()` rufen — das zu vergessen hat die App von P2 bis P4 beim Öffnen der Einstellungen abstürzen lassen. | `NotificationDispatcher.android.kt` | P8/P9 |
| Die Repository-Fakes liegen in `composeApp/commonTest/testutil/fakes/`, nicht in einem `:core:testing`. Bewusst so, solange alle Tests in `:composeApp` liegen — beides zusammen umziehen. | `testutil/fakes/` | P9 |
| Tests liegen alle in `:composeApp`, nicht in ihren Modulen. Die Gates bleiben dadurch unverändert; der Umzug braucht pro Modul eigene Test-Abhängigkeiten. | `composeApp/src/commonTest` | P9 |
| Der Widget-UseCase liefert bei einem Lesefehler des Caches eine leere Liste statt eines Fehlers — ein Widget hat keine Fehlerdarstellung. Bewusst so, im Code begründet. | `WidgetTimetableUseCase.kt` | — |
| 48 × `catch (e: Exception)` übrig (von ursprünglich 69). Drei Sorten, alle bewusst: die Parser (schlucken eine kaputte Zeile, nicht die Seite), die Klassifikationsstellen selbst (`toAppError`, DB-Zugriffe in den Repositories), und die Plattformschicht (FileViewer, Dispatcher, Scheduler, DNS, SecureStorage). Im Dualis-Datenpfad ist keines mehr. | `:data`, `:services`, `:composeApp` | — |

---

## 7. Nächster Schritt: P6 — Room-Migrationen

Der Plan beschreibt P6 in §2. **Die offene Frage ist beantwortet:**

> **Entscheidung des Nutzers (2026-08-21): der iOS-DB-Umzug synchronisiert neu, er kopiert nicht.**
> Die Datenbank ist ein Dualis-Cache und kein Primärspeicher — es geht nichts verloren, was sich
> nicht wiederbeschaffen ließe. Konkret: alte Datei am alten Ort löschen, neue in der App-Group
> leer anlegen, der nächste Abruf füllt sie. Kein Kopierpfad, keine Zwei-Orte-Logik. Der Nutzer
> sieht beim ersten Start nach dem Update einen kurzen Ladevorgang statt sofortiger Daten.

**Was noch bereitstehen muss, bevor der iOS-Teil verifizierbar ist:** die **App-Group** im
Apple-Developer-Portal (Entitlements, Provisioning). Der Plan hatte sie für P8 notiert, sie wird
aber schon hier gebraucht. Ohne sie lässt sich der Umzug bauen, aber nicht auf dem Simulator
prüfen — dann den iOS-Teil vorbereiten, klar als unverifiziert kennzeichnen und den Rest liefern.

Ausgangslage: Schema-Version **4**, `exportSchema = true`, Export unter
`data/schemas/de.fampopprol.dhbwhorb.data.storage.database.AppDatabase/4.json`. Die drei anderen
Ordner unter `data/schemas/` sind Altlasten aus der Zeit vor dem Rename (`de.joinside.dhbw.*`) und
gehören nicht zur aktuellen Datenbank.

Reihenfolge, die ich wählen würde:

1. `fallbackToDestructiveMigration` aus allen vier `DatabaseFactory.*.kt` entfernen und durch
   echte `Migration`-Objekte ersetzen. Ohne eine Schema-Änderung in dieser Phase ist die
   Migrationsliste zunächst leer — das ist in Ordnung, entscheidend ist, dass ein künftiges Update
   nicht mehr stillschweigend löscht.
2. Migrationstests. `androidx.room:room-testing` gibt es in derselben Version wie den Rest
   (2.8.4, steht als `room` in `libs.versions.toml`) und bringt `MigrationTestHelper` mit; die
   exportierten Schemas sind genau dafür da. Mindestens: 4 → 5 mit echten Daten, und der Nachweis,
   dass eine Datenbank auf Version 4 nach dem Update noch ihre Zeilen hat.
3. Die iOS-Datenbank aus `NSDocumentDirectory` in den App-Group-Container verschieben
   (`NSFileManager.containerURLForSecurityApplicationGroupIdentifier`), damit die Widget-Extension
   sie lesen kann. Neu-Sync statt Kopieren, siehe Entscheidung oben.
4. Prüfen, ob macOS denselben Umzug braucht — `DatabaseFactory.macos.kt` hat dasselbe Muster.

**Womit man in P6 rechnen sollte:** Der Umzug ändert nichts, was ein Test sieht. Das ist genau die
Sorte Phase, in der die Regel aus §4.4 zählt — nach dem Umzug die App auf beiden Plattformen
starten, Daten prüfen, und auf iOS zusätzlich das Widget.

P6 ist Voraussetzung für **P7** (natives SwiftUI), weil das Widget dort auf dieselbe Datenbank
zugreift. P5 und P6 waren laut §3 unabhängig; ab hier ist die Kette wieder linear.

---

## 8. Zur Parallelisierung

Der Nutzer hat nach Subagents und Worktrees gefragt. Einschätzung nach sechs Phasen:

* **Über Phasen hinweg lohnt es sich nicht.** Die Kette ist bindend, und jede Phase schreibt
  dieselben zentralen Dateien um. P3 hat fast jede Datei im Datenpfad angefasst, P4 die gesamte
  Präsentationsschicht.
* **Innerhalb einer Phase über disjunkte Dateimengen schon.** P7 (fünf SwiftUI-Screens) ist der
  nächste echte Fan-out. P6 ist dafür zu klein und zu verzahnt.
* Voraussetzung ist, dass die gemeinsamen Verträge **vorher** stehen. In P3 waren das
  `Outcome`/`AppError` plus die Repository-Interfaces, in P4 `Store`/`BaseStore`/`EffectScope` —
  beide Male zuerst geschrieben, beide Male hat es getragen. Für P7 wäre es entsprechend die
  Swift-seitige Store-Anbindung, bevor die Screens verteilt werden.

---

## 9. Was in den letzten Phasen gelernt wurde

Kurz, weil es sich wiederholt hat:

* **Ein grünes Gate beweist wenig.** Beide teuren Funde in P4 (Absturz in den Einstellungen,
  Nachladen beim Tab-Wechsel) kamen vom Durchklicken, nicht von Tests. Siehe §4.4.
* **Reinheit strukturell machen, nicht versprechen.** Die Reducer sind Top-Level-Funktionen, weil
  sie dann kein Repository und keine Uhr erreichen *können*. Ein Kommentar „ist pur" hätte
  denselben Anspruch erhoben und nichts garantiert.
* **Wenn zwei Dinge dasselbe wissen, driften sie.** Drei Kopien der Re-Auth-Schleife waren
  auseinandergelaufen (P3), `AppScreen` neben dem Back-Stack wäre die nächste Kopie gewesen (P5).
  Beim Einbau einer zweiten Quelle für dieselbe Wahrheit: nicht.
* **Bei Testerwartungen, die dem neuen Verhalten widersprechen, erst prüfen wer recht hat.** In
  P-1 hatten zwei Tests unrecht und einer recht — der eine hat einen echten Bug gefunden. In P4 hat
  ein Reducer-Test eine echte Lücke aufgedeckt (leere Woche ≠ ungeladene Woche).
