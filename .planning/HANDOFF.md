# Handoff — v3-Umbau

> Stand: 2026-08-22 · **P-1 bis P9 abgeschlossen**, dazu fünf Arbeiten außerhalb der Phasenkette
> P9 liegt auf `phase/p9-cleanup` und ist **noch nicht nach `v3` gemergt** — das wartet auf die
> Freigabe des Nutzers
> Arbeitsverzeichnis sauber · nichts gepusht
> **Die Phasenkette ist damit zu Ende.** Was offen bleibt, steht in §6 und wartet auf ein echtes
> Gerät oder auf eine Entscheidung, nicht auf eine Phase.

`v3` steht auf dem Stand nach P8; P9 hängt daneben:

```bash
git -C <v3-worktree> log --oneline -1   # merge: lecture reminders into v3
git log --oneline -1 phase/p9-cleanup   # test: make the instrumented suite runnable, and run it
git checkout v3 && git merge --no-ff phase/p9-cleanup -m "merge: P9 cleanup into v3"
```

**`phase/p9-cleanup` ist von einem Worktree-Branch aus angelegt**, nicht direkt von `v3` — `v3`
war in einem anderen Worktree ausgecheckt. Der Commit darunter ist derselbe; der Merge geht
sauber durch.

**Was seit dem letzten Handoff dazugekommen ist**, in der Reihenfolge der Commits:

| | |
|---|---|
| **P8 — iOS-Plattformdienste nativ** | Widget liest die App-Group-Datenbank direkt, BGTaskScheduler in Kotlin, Keychain-Gruppe. Details im Plan unter „Was tatsächlich passiert ist". |
| **Zugangsdaten-Wächter** | Deinstallieren nimmt das Konto mit — der Schlüsselbund tut das nicht von selbst. `CredentialsInstallGuard`, plus Backup-Ausschluss auf Android. |
| **Stundentakt** | Die Prüfung lief alle 15 Minuten, jetzt stündlich; auf Android brauchte es dafür `ExistingPeriodicWorkPolicy.UPDATE`. |
| **Änderungserkennung neu** | Verschiebungen werden als Verschiebungen erkannt, nichts Vergangenes wird mehr gemeldet, zwei Geschwindigkeiten (laufende Woche voll, vier Zukunftswochen per Raster), Cache räumt sich auf, Texte übersetzt. |
| **Erinnerung vor Vorlesungen** | Zweite Benachrichtigungsart, vom Betriebssystem geplant statt gepollt. Neue Berechtigungen auf Android. |
| **P9 — Aufräumen** | Letzter Service-Locator weg, Tests in ihre Module, iOS-Widget lokalisiert, Apple-Job in der CI, Logs entkästelt, PROJECT.md nachgezogen. Details unten und im Plan. |

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
git checkout -b phase/p7-swiftui v3
# … arbeiten, Gate grün bekommen …
git commit
# anhalten, berichten, auf Freigabe warten
git checkout v3 && git merge --no-ff phase/p7-swiftui -m "merge: P7 … into v3"
```

Commit-Messages: erklären **warum**, nicht was. Die bisherigen Phasen-Commits sind das Muster —
sie benennen auch, was schiefging und was bewusst nicht gemacht wurde.

---

## 3. Umgebung

**`ANDROID_HOME` ist nicht gesetzt und es gibt keine `local.properties`.** Ohne das schlägt jeder
Android-Task mit „SDK location not found" fehl. Jedem Gradle-Aufruf voranstellen:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew testDebugUnitTest desktopTest
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
  Mac-Tastatur, aus `@` wird `"` und aus `-` ein `ß`. **Der Weg um das Problem herum ist die
  Zwischenablage** (in P7 gefunden, damit ist der Demo-Login erreichbar):

  ```bash
  printf 'demo@hb.dhbw-stuttgart.de' | xcrun simctl pbcopy 8D76539B-73DF-410F-B550-027260B1BAAD
  ```

  Dann im Feld lange drücken (`touch_path` mit zwei Punkten auf derselben Stelle, ~1200 ms) und
  „Einsetzen" antippen. Das Passwort `demo123` hat keine Sonderzeichen und lässt sich tippen.

* **Vorsicht beim langen Drücken im E-Mail-Feld: der Simulator hat ein echtes DHBW-Konto im
  Schlüsselbund.** Statt des Einsetzen-Menüs kann iOS-Passwort-Autofill aufgehen — und das
  füllt *und sendet* den Login mit dem echten Konto des Nutzers. In P7 ist genau das passiert:
  die App hat danach echte Dualis-Requests gemacht und in der Vorschau lag ein persönliches
  Dokument. Erst den Screenshot ansehen, ob wirklich „Einsetzen" dasteht, und wenn das Konto
  angemeldet ist, es dem Nutzer sagen statt es unbemerkt weiterzubenutzen.
* **Desktop-TLS ist behoben, aber wissenswert:** Dualis kettet auf HARICAs 2021-Root, den kein
  JDK-`cacerts` kennt (auch Temurin 17 in der CI nicht). Die Desktop-App kam deshalb überhaupt
  nicht an Dualis heran. `DesktopTrustStore.kt` bündelt die zwei Roots; Details in
  `data/src/desktopMain/resources/certs/README.md`. Wenn Desktop wieder „PKIX path building
  failed" sagt, hat Dualis vermutlich die Kette gewechselt — dort nachsehen, nicht im Netzwerkcode.
* **`:composeApp:desktopTest` fasst den echten Schlüsselbund an.** `KoinGraphTest.graph_actuallyBuilds`
  baut absichtlich den realen Graphen, also auch das echte `DesktopSecureStorage` — und das
  migriert beim ersten Zugriff die alten Einträge in das gebündelte. Kein Datenverlust, aber es
  erklärt, warum ein Testlauf den Schlüsselbund verändert.
* Auf dem **Android-Emulator ist eine echte Sitzung gespeichert.** Die App startet dort eingeloggt
  und mit echten Daten — praktisch zum Durchklicken, aber Vorsicht: das sind reale Dualis-Requests.
* **Der iOS-Simulator ist seit dem 22.08.2026 abgemeldet.** Beim Nachweis des Install-Guards
  wurde die App deinstalliert; damit sind die Zugangsdaten weg, und ein Durchklicken hinter dem
  Login braucht dort wieder einen Login von Hand. Der Weg über die Zwischenablage steht weiter
  unten.
* **`simctl uninstall` meldet auf iOS jetzt ab — das ist der Guard, kein Fehler.** Der
  Schlüsselbund überlebt eine Deinstallation, der App-Group-Container nicht;
  `CredentialsInstallGuard` vergleicht dessen Erstellungsdatum mit dem gespeicherten Stempel. Am
  22.08. an allen drei Fällen belegt: Neuinstallation räumt auf („Credentials belong to a previous
  installation"), zweiter Start derselben Installation schweigt, `install` über die bestehende App
  (der Update-Fall) schweigt ebenfalls. **Wer die angemeldete Sitzung behalten will,
  deinstalliert nicht** — ein `install` drüber genügt.
* **Widget-Daten kommen aus der App-Group-Datenbank, und die lässt sich von Hand füllen.** Für
  Prüfungen, in denen der echte Stundenplan leer ist (Praxisphase), ist das der Weg zu sichtbaren
  Vorlesungen ohne weitere Dualis-Requests — und der einzige, der beweist, dass die Extension
  wirklich selbst liest, weil man einen Wert schreiben kann, den die App nie gesehen hat:

  ```bash
  DB=$(find ~/Library/Developer/CoreSimulator/Devices/8D76539B-73DF-410F-B550-027260B1BAAD/data/Containers/Shared/AppGroup -name grades_database.db)
  sqlite3 "$DB" "INSERT INTO lecture (shortSubjectName, fullSubjectName, startTime, endTime, location, isTest) VALUES ('TEST','Testvorlesung','2026-08-22T08:15:00','2026-08-22T11:30:00','RAUM',0);"
  ```

  Danach die App einmal starten (das löst `reloadAllTimelines()` aus) oder im Widget-Kontextmenü
  die Größe wechseln — das erzwingt eine frische Timeline auch ohne laufende App. **Hinterher
  aufräumen**, sonst stehen Phantomvorlesungen im Cache des Nutzers.
* **Ein iOS-Widget lässt sich nicht per `simctl` neu zeichnen.** Ein Neustart des Simulators und
  ein `launchctl kickstart` von SpringBoard zeigen beide weiter die gespeicherte Timeline. Was
  wirklich neu lädt: die Widget-Größe im Kontextmenü umschalten.
* **Der Schalter „Benachrichtigungen aktivieren" lässt sich auf dem Simulator nicht umlegen**,
  solange die Systemabfrage nicht beantwortet wurde — `requestAuthorization` liefert dann `false`
  und der Store bleibt korrekterweise aus. Zum Prüfen der Folgelogik die Voreinstellung direkt
  setzen und die App neu starten:

  ```bash
  xcrun simctl spawn 8D76539B-73DF-410F-B550-027260B1BAAD defaults write de.fampopprol.dhbw notifications_enabled -string true
  ```
* **Die Android-Datenbank und die Einstellungen lassen sich von Hand setzen** — der Weg, auf dem
  Erinnerungen und Änderungserkennung ohne echte Dualis-Requests prüfbar sind. `sqlite3` gibt es
  auf dem Emulator nicht, also über `/data/local/tmp` hin und zurück, und die App vorher beenden:

  ```bash
  ADB=$HOME/Library/Android/sdk/platform-tools/adb
  $ADB shell am force-stop de.fampopprol.dhbwhorb
  for f in grades_database.db grades_database.db-wal grades_database.db-shm; do
    $ADB exec-out "run-as de.fampopprol.dhbwhorb cat databases/$f" > "/tmp/gdb.db${f#grades_database.db}"
  done
  sqlite3 /tmp/gdb.db "INSERT INTO lecture (...) VALUES (...); PRAGMA wal_checkpoint(TRUNCATE);"
  for f in db db-wal db-shm; do $ADB push /tmp/gdb.$f /data/local/tmp/gdb.$f; done
  $ADB shell 'run-as de.fampopprol.dhbwhorb sh -c "cp /data/local/tmp/gdb.db databases/grades_database.db; …"'
  ```

  `exec-out` und nicht `shell`, sonst zerstören Zeilenenden die Datei. Das `wal_checkpoint` ist
  nötig, weil der lokale `sqlite3` sonst nur ins WAL schreibt. Einstellungen liegen daneben in
  `shared_prefs/dualis_settings.xml` und gehen denselben Weg. **Hinterher aufräumen.**
* **Berechtigungen auf dem Emulator setzt man mit `pm grant` und `appops`:**

  ```bash
  $ADB shell pm grant de.fampopprol.dhbwhorb android.permission.POST_NOTIFICATIONS
  $ADB shell cmd appops set de.fampopprol.dhbwhorb SCHEDULE_EXACT_ALARM allow   # oder ignore
  ```

  Ob ein Wecker wirklich steht — und ob exakt —, sagt `adb shell dumpsys alarm | grep -A 2 reminder_`:
  `window=0 exactAllowReason=permission` heißt exakt, `window=+5m0s0ms` heißt Fallback.
* **`installDebug` löscht alle gestellten Wecker.** Eine Neuinstallation zieht die `PendingIntent`s
  mit; wer auf das Feuern eines Alarms wartet, darf zwischendurch nicht neu installieren. Erst
  installieren, dann stellen, dann warten — sonst wartet man auf nichts.
* **Ein Xcode-Target, das `Shared.framework` linkt, braucht zwei Dinge.** Die Gradle-Build-Phase
  (`:shared:embedAndSignAppleFrameworkForXcode`) als *erste* Phase — die Extension wird vor der App
  gebaut, sonst gibt es das Framework noch nicht — und `ARCHS = arm64`, weil das Kotlin-Framework
  keine x86_64-Scheibe hat. Der Fehler ohne das zweite ist „Undefined symbols for architecture
  x86_64" und sieht aus wie ein Linkerproblem, ist aber eine Architekturfrage.

---

## 4. Verifikation — das Gate

Eine Phase ist erst fertig, wenn alles Folgende grün ist.

### 4.1 Tests

**Die Tasknamen sind seit P9 unqualifiziert** — die Tests liegen jetzt in ihren Modulen, und
`:composeApp:desktopTest` allein deckt nur noch die Compose-UI ab.

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew testDebugUnitTest desktopTest --rerun-tasks
```

**Sollwerte auf dem Stand von `phase/p9-cleanup`:** `testDebugUnitTest` **318**, `desktopTest`
**424**, 0 Fehler, **0 übersprungen**. (Vor P9 waren es 322 und 428; die vier Differenz sind
`DatabaseFactoryTest`, gelöscht — kein Test ist beim Umzug verlorengegangen, das war die Prüfung,
auf die es dabei ankam.) Es gibt seit P4 keinen einzigen `@Ignore` mehr im Projekt — wenn einer
auftaucht, gehören ein Grund und eine Phase dazu.

Gradle meldet die Zahlen nicht zuverlässig, also selbst zählen:

```bash
python3 - <<'EOF'
import glob, re
for d in ["testDebugUnitTest", "desktopTest"]:
    t = f = sk = 0
    for x in glob.glob(f"*/build/test-results/{d}/*.xml") + glob.glob(f"core/*/build/test-results/{d}/*.xml"):
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
for x in glob.glob("*/build/test-results/desktopTest/*.xml"):
    s = open(x, encoding="utf-8", errors="replace").read()
    for m in re.finditer(r'<testcase name="([^"]+)"[^>]*>\s*<failure[^>]*message="([^"]*)"', s):
        print(m.group(1), "->", html.unescape(m.group(2))[:300])
EOF
```

**Neue Compose-UI-Tests im Wurzelpaket** brauchen einen Eintrag in der Ausschlussliste in
`composeApp/build.gradle.kts` — der bestehende Filter deckt nur `**/ui/**/*Test.class` ab, alles
darüber läuft sonst auch als Android-Unit-Test und scheitert dort.

**Ein neuer Test gehört in das Modul, das er testet**, nicht mehr nach `:composeApp`. Die Fakes
(`FakeRepositories`, `MockAppDatabase`, `testKoin()`) liegen in `:core:testing` und stehen jedem
`commonTest` zur Verfügung. Zwei Fallen dabei: **Backtick-Namen dürfen kein Komma enthalten** —
Kotlin/Native lehnt sie ab, und anders als früher wird `commonTest` auch für Apple-Targets
kompiliert. Und ein Modul ohne `isReturnDefaultValues` in `testOptions` lässt jeden
Android-Unit-Test, der loggt, in `android.util.Log` sterben; alle vier haben es inzwischen.

**Die instrumentierten Tests stehen in keinem Gate** — sie brauchen ein Gerät. Sie laufen aber
wieder (`:composeApp:connectedDebugAndroidTest`, 3 Tests): bis P9 fehlten dem Source-Set sowohl
die Test-Abhängigkeiten als auch `androidx.test:runner`, also kompilierte es seit dem
Modulschnitt nicht einmal.

### 4.2 Das Framework muss Compose-frei bleiben

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
nm -gU shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Shared \
  | grep -c androidx.compose        # muss 0 sein
```

Seit P4 ist `:presentation` Teil des Frameworks; seit P7 hängt die iOS-App daran. Eine
Compose-Abhängigkeit dort einzuschleppen kompiliert problemlos und bricht die SwiftUI-App —
diese Prüfung ist das, was es merkt. `:composeApp` hat seit P7 **keine iOS-Targets mehr**, der
Xcode-Build ruft `:shared:embedAndSignAppleFrameworkForXcode`.

### 4.3 iOS-Build (braucht keine Signatur)

**Achtung, seit P6:** `CODE_SIGNING_ALLOWED=NO` bettet **keine Entitlements** ein. Die App startet,
aber App Group und Keychain-Access-Group fehlen — der Container-Lookup scheitert mit „client is not
entitled", und die Datenbank landet im Fallback-Pfad statt im geteilten Container. Wer etwas prüft,
das an einem Entitlement hängt (Datenbankort, Widget, Keychain), baut ohne den Schalter:
`xcodebuild … -derivedDataPath /tmp/dhbwsigned build` — der Simulator signiert dann selbst
(„Sign to Run Locally") und übernimmt die Entitlements aus der `.entitlements`-Datei. Ein
Apple-Developer-Portal-Eintrag ist dafür **nicht** nötig; für echte Geräte schon.

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

Also nach jeder Phase: **beide Apps starten und jeden Screen einmal öffnen.** Seit P8 gehört das
Widget dazu — es ist ein zweiter Prozess mit eigenem Koin-Start, der von keinem Test und keinem
App-Durchlauf berührt wird. Und seit den Erinnerungen gehört dazu, dass ein gestellter Wecker
wirklich feuert: `dumpsys alarm` zeigt nur, dass etwas eingetragen ist, nicht dass es ankommt.
Beides steht in §3 als Rezept.

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
über `adb exec-out screencap -p` und `adb shell input tap X Y`. Die Android-Tab-Leiste liegt bei
y ≈ 2655; die vier Tabs bei x ≈ 150 / 479 / 802 / 1120.

**Auf iOS rechnet das Tool in Punkten, der Screenshot ist in Pixeln** (iPhone 17: 402 pt breit,
918 px im Bild → Faktor 2,284). Die Tab-Leiste liegt bei y ≈ 823 pt, die vier Tabs bei
x ≈ 70 / 155 / 235 / 327.

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
:core:testing    Fakes, Mocks, testKoin() — hängt an keiner Produktionsabhängigkeit,
                 wird nur aus einem commonTest heraus benutzt
:domain          Dualis- und Domänenmodelle, Repository-Interfaces, UseCases, TimeHelper
                 ← keine Frameworks
:data            Ktor, Parser, Dualis-Services, Gateway, ReAuthenticator, Room + DAOs,
                 SecureStorage, Prefs, Repository-Impls, dataModule
:services        Notifications, Widget-UseCases, FileViewer
:presentation    MVI-Stores ← keine Compose-Runtime, im Shared.framework enthalten
:shared          initKoin() + Umbrella → Shared.framework für Apple,
                 dazu die Swift-Brücke in iosMain/…/ios/
:composeApp      Compose-UI, Navigation, Entry Points, Glance-Widget
                 ← seit P7 ohne iOS-Targets (Android, Desktop, macOS)
                 ← seit P9 nur noch die eigenen Tests: UI, Navigation, Koin-Graph
iosApp/          SwiftUI-App: RootView + fünf Screens, linkt Shared.framework
iosApp/TimetableWidget/
                 Widget-Extension: **eigener Prozess mit eigenem Koin-Graphen**, linkt
                 Shared.framework ebenfalls und liest die App-Group-DB direkt (seit P8)
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
| Store ↔ Swift | `shared/src/iosMain/…/ios/` (`SharedApp`, `StoreBridges`, `FlowObserver`) und `iosApp/iosApp/Bridge/StoreBox.swift` |
| Widget ↔ Kotlin | `shared/src/iosMain/…/ios/WidgetSnapshot.kt` — die **einzige** Beschreibung der Widget-Daten; die Extension startet dort ihren eigenen Koin-Graphen |
| iOS-Hintergrundlauf | `services/…/notifications/LectureMonitorScheduler.ios.kt` (BGTaskScheduler), registriert in `iOSApp.init()`, ein-/ausgeschaltet in `AppModel` |
| Änderungserkennung | `services/…/notifications/LectureChangeMonitor.kt` — zwei Geschwindigkeiten und die Paarung, die eine Verschiebung erkennt |
| Erinnerungen | `services/…/reminders/` — `LectureReminderPlanner` plant, die drei `…ReminderScheduler` übergeben ans System |
| Android-Weckerrechte | `services/src/androidMain/AndroidManifest.xml` — eigenes Manifest im Modul, wird gemerged |
| Benachrichtigungstexte | `services/…/notifications/LectureNotificationTexts.kt` (de/en) — der einzige Nutzertext außerhalb der drei Ressourcensysteme |
| Widget-Texte (iOS) | `iosApp/TimetableWidget/Localizable.xcstrings` — eigenes Bundle, seit P9; die Extension erbt den Katalog der App **nicht** |
| SwiftUI-Screens | `iosApp/iosApp/RootView.swift` + `iosApp/iosApp/Screens/` |
| iOS-Farben, Karten | `iosApp/iosApp/Design/Theme.swift` — `Color.brand` und `.tint()` an der Wurzel sind der iOS-Ersatz für die Material-You-Seed-Farbe |
| Wochenraster (iOS) | `iosApp/iosApp/Screens/TimetableGrid.swift` — Spurenlayout für parallele Vorlesungen |
| Semester-Reihenfolge | `domain/…/model/SemesterOrder.kt` — sortiert und gruppiert wird einmal im `GradesStore`, beide UIs lesen `GradesState.sections` |
| iOS-Texte | `iosApp/iosApp/Localizable.xcstrings` (en/de) — getrennt von den Compose-Ressourcen |
| Navigation | `composeApp/…/ui/navigation/Routes.kt`, `DhbwNavHost.kt` |
| Test-Graph | `core/testing/…/testutil/TestKoin.kt` — `testKoin()`; das Composable `WithTestKoin { }` liegt in `composeApp/src/commonTest/…/testutil/WithTestKoin.kt`, weil `:core:testing` kein Compose kennen darf |
| Repository-Fakes | `core/testing/…/testutil/fakes/FakeRepositories.kt` |
| Store-Test-Helfer | `core/testing/…/presentation/StoreTestSupport.kt` |
| Graph-Prüfung | `composeApp/src/desktopTest/…/di/KoinGraphTest.kt` — bei neuen Bindungen mitpflegen |
| Migrations-Gate, Truststore-Test | seit P9 in `data/src/desktopTest/` |
| Geheimnisse vs. Einstellungen | `data/…/storage/settings/` — `PlatformSettings`, `SettingsStorage` |
| Desktop-Truststore | `data/…/net/DesktopTrustStore.kt` + `data/src/desktopMain/resources/certs/` |
| Schema-Version, Migrationen | `data/…/database/AppDatabaseMigrations.kt` — `APP_DATABASE_VERSION` ist die einzige Quelle |
| Öffnungspolitik der DB | `data/…/database/DatabaseFactory.kt` — `createRoomDatabase()`; die vier Actuals wählen nur den Pfad |
| Migrations-Gate | `composeApp/src/desktopTest/…/data/database/AppDatabaseMigrationTest.kt` |

---

## 6. Bekannte, bewusst offene Punkte

Alle sind im Code kommentiert und im Plan vermerkt — nichts davon ist vergessen worden.

| Punkt | Ort | Fällig |
|---|---|---|
| Auf einem **echten** iOS-Gerät ist die App Group `group.de.fampopprol.dhbwhorb` erst nutzbar, wenn sie im Apple-Developer-Portal registriert ist. Fehlt sie, protokolliert die App „App group … unavailable" und legt die DB im Dokumentverzeichnis ab — und seit P8 hängt das ganze Widget daran, nicht mehr nur ein JSON-Snapshot. Dasselbe gilt für die Keychain-Gruppe und für `BGTaskSchedulerPermittedIdentifiers`. Auf dem Simulator ist nichts davon ein Thema. | `DatabaseFactory.ios.kt`, beide `.entitlements` | **vor dem ersten Gerätetest** (nur Portal, kein Code) |
| Die Stores sind Applikations-Singles, nicht im Navigations-Scope. Bewusst so: ein Halter je Navigationseintrag würde bei jedem Tab-Wechsel neu laden — genau das, was P4 abgestellt hat. Deshalb bleibt auch `EnsureLoaded` neben `Load` bestehen. | `PresentationModule.kt`, `GradesStore`, `DocumentsStore` | — |
| Läuft die Sitzung ab, während man eingeloggt im Graphen steht, zeigt die Seite „bitte anmelden" statt zur Login-Wurzel zurückzukehren. Ehrlich, aber nicht schön. | `GradesPage`, `DocumentsPage` | P9 |
| **Im Demo-Modus gibt es keine Noten.** `DualisGradeService` hat als einziger Dienst keinen Demo-Zweig, der Abruf endet in `AppError.SessionExpired`. Gilt auf allen Plattformen, stammt aus der Zeit vor dem Umbau — beim Durchklicken von P7 aufgefallen. | `DualisGradeService.kt` | offen |
| **Prüfungen werden farblich unterschieden, aber fast nie erkannt.** Der Block wird bernsteinfarben, wenn `Lecture.isTest` gesetzt ist — und das setzt `TimetableParser` nur bei `background-color:#FF6666` in der Dualis-Zelle. Einträge, die bloß „Mündliche Prüfung" heißen, tragen die Markierung nicht. Offene Entscheidung des Nutzers: zusätzlich am Titel erkennen (Heuristik im Parser, wirkt auf allen Plattformen) oder erst prüfen, ob die Farbmarkierung heute woanders steht — dafür braucht es das rohe HTML einer Woche mit echter Prüfung. | `TimetableParser.kt:79` | offen, wartet auf den Nutzer |
| **Das Sichern eines Dokuments in „Dateien" ist weiterhin nur durch Tests belegt.** Die Semester-Reihenfolge ist in P8 am Simulator gesehen worden (WiSe 2024/25 vor SoSe 2025, mit echten Noten). | `DocumentsScreen.swift` | beim nächsten Gerätetest |
| **VoiceOver auf iOS ist statisch geprüft, nicht durchlaufen.** Jedes Bedienelement trägt ein `Text`-Label, Listenzeilen sind zu einem Element zusammengefasst, Symbole haben `accessibilityLabel` — aber niemand ist mit eingeschaltetem VoiceOver durchgegangen. In P8 wieder nicht: was VoiceOver *sagt*, steht in keinem Screenshot, das braucht einen Menschen mit eingeschaltetem Screenreader. | `iosApp/iosApp/Screens/` | offen, braucht einen Menschen |
| **Das Widget lebt mit Room, Ktor und Koin in einem Prozess mit engem Speicherlimit.** Auf dem Simulator unauffällig; auf einem Gerät sind ~30 MB die Grenze für eine Widget-Extension. Wenn das Widget dort leer bleibt, ist das der erste Verdacht — und die Antwort wäre ein kleinerer Graph nur für die Extension. | `WidgetSnapshot.kt` | beim ersten Gerätetest |
| **Dass der Background-Task auch feuert, ist ungeprüft.** Registrierung und Einreichung sind auf dem Simulator belegt, das Auslösen nicht — dafür braucht es ein Gerät am Debugger (`_simulateLaunchForTaskWithIdentifier`). | `LectureMonitorScheduler.ios.kt` | beim nächsten Gerätetest |
| **Benachrichtigungs-Kategorien und -Actions („Woche öffnen") gibt es nicht.** Der Plan hatte sie für P8 vorgesehen; da `NotificationDispatcher.ios.kt` bereits eine vollständige Kotlin-Zustellung ist, wurde sie nicht nach Swift kopiert — und die Actions damit auch nicht gebaut. Der Text selbst ist seit dem Monitor-Umbau übersetzt. | `NotificationDispatcher.ios.kt` | offen |
| Der Widget-UseCase liefert bei einem Lesefehler des Caches eine leere Liste statt eines Fehlers — ein Widget hat keine Fehlerdarstellung. Bewusst so, im Code begründet. | `WidgetTimetableUseCase.kt` | — |
| **Der Rastervergleich der Zukunftswochen sieht keine Dozenten- und keine Detail-Raumwechsel.** Das Wochenraster kennt beides nicht, und seine Raumangabe ist eine andere Zeichenkette als die gespeicherte. Bewusst: die laufende Woche wird vollständig geprüft, in Woche +3 fällt so etwas auf, sobald sie zur laufenden wird. | `LectureChangeMonitor.checkFutureWeekByGrid()` | — |
| **Eine Vorlesung, die in eine andere Woche verschoben wird, bleibt Absage plus Neuanlage.** Die Paarung läuft innerhalb einer Woche. Wochenübergreifend zu paaren hieße, alle beobachteten Wochen gleichzeitig zu vergleichen. | `LectureChangeMonitor.diff()` | offen |
| **Auf iOS ist die Erinnerung nur bis zur Übergabe an das System belegt, nicht bis zur Zustellung.** Der Simulator hat keine erteilte Benachrichtigungserlaubnis, und die App ist dort abgemeldet. Auf Android ist die Zustellung nachgewiesen (Alarm um 10:51:00, Benachrichtigung sichtbar). | `IosLectureReminderScheduler` | beim nächsten Gerätetest |
| **Ohne `SCHEDULE_EXACT_ALARM` kommen Erinnerungen mit bis zu fünf Minuten Verzug.** Ab Android 14 erteilt der Nutzer die Berechtigung selbst; die App zeigt einen Hinweis und einen Knopf dorthin, drängt aber nicht. `USE_EXACT_ALARM` (auto-erteilt) wäre die Alternative — Play beschränkt es auf Wecker- und Kalender-Apps, das wäre eine Entscheidung mit Review-Risiko. | `AndroidLectureReminderScheduler` | offen, wenn Verzug stört |
| **Erinnerungen werden nur alle 40 Stück und 14 Tage weit geplant.** iOS hält höchstens 64 wartende Anfragen und verwirft den Rest stillschweigend; der stündliche Lauf schiebt nach. Bei mehr als 40 Vorlesungen in 14 Tagen fehlen die hintersten, bis eine frühere vorbei ist. | `LectureReminderPlanner.MAX_REMINDERS` | — |
| **Die Meldungstexte sind der einzige Nutzertext außerhalb der Ressourcensysteme.** Begründet (ein Hintergrund-Worker hat weder Compose- noch Bundle-Kontext), aber es ist eine weitere Stelle, an der Sprache lebt — inzwischen die vierte neben Compose-Ressourcen, dem App-Katalog und dem Widget-Katalog. | `LectureNotificationTexts.kt` | — |
| **`NotificationSettingsCard.kt` enthält englische Literale statt String-Ressourcen** („Notification permission denied…", „Check completed"). In P9 beim Durchsehen aufgefallen, nicht angefasst — es ist Compose-UI und gehört in `composeResources`, nicht in eine Aufräumphase, die den Umfang schon gesprengt hatte. | `NotificationSettingsCard.kt` | offen |
| 48 × `catch (e: Exception)` übrig (von ursprünglich 69). Drei Sorten, alle bewusst: die Parser (schlucken eine kaputte Zeile, nicht die Seite), die Klassifikationsstellen selbst (`toAppError`, DB-Zugriffe in den Repositories), und die Plattformschicht (FileViewer, Dispatcher, Scheduler, DNS, SecureStorage). Im Dualis-Datenpfad ist keines mehr. | `:data`, `:services`, `:composeApp` | — |

---

## 7. Nächster Schritt

**Die Phasenkette ist zu Ende.** P9 war die letzte, und was von ihr zu tun war, ist getan. Der
konkrete nächste Schritt ist deshalb keiner im Code, sondern:

1. **P9 durchsehen und nach `v3` mergen** (Befehl oben im Kopf). Danach ist `v3` der vollständige
   Umbau.
2. **Die drei Portal-Einträge anlegen** — App Group `group.de.fampopprol.dhbwhorb`, die
   Keychain-Gruppe und `BGTaskSchedulerPermittedIdentifiers`. Kein Code, aber ohne sie ist auf
   einem echten Gerät die Datenbank am falschen Ort, das Widget blind und der Hintergrund-Task
   abgelehnt. Das blockiert alles unter „beim nächsten Gerätetest" in §6.
3. **Einen Gerätetest machen.** Sechs der offenen Punkte in §6 warten darauf und auf nichts
   sonst: VoiceOver, das Feuern des Background-Tasks, die Zustellung der iOS-Erinnerung, das
   Sichern eines Dokuments in „Dateien", das Speicherlimit der Widget-Extension, die App Group.
4. **Die beiden Entscheidungen treffen**, die in §6 auf den Nutzer warten: die Prüfungserkennung
   im Parser (Heuristik am Titel oder erst rohes HTML einer Prüfungswoche ansehen) und ob der
   Verzug ohne `SCHEDULE_EXACT_ALARM` stört.

**Was P9 gemacht hat**, in Commit-Reihenfolge:

| | |
|---|---|
| Service-Locator | `NotificationDispatcher` ist ein Interface mit vier Implementierungen; der Android-Context kommt aus Koin. `FileViewer` las dasselbe statische Feld und holt sich den Context jetzt ebenfalls aus dem Graphen. `DualisApplication.onCreate()` initialisiert nichts mehr. |
| Tests, die keine waren | `DatabaseFactoryTest` gelöscht (vier Prüfungen auf „Funktionsreferenz ≠ null"). `BackgroundServicesIntegrationTest` kompilierte seit dem Modulschnitt nicht — sechs seiner acht Tests endeten in `assertTrue(true)`; übrig sind zwei, die scheitern können, und sie laufen wieder. |
| Logs | Kästchengrafik und Emoji aus den drei `LectureMonitorScheduler` raus. |
| iOS-Widget | Eigener String-Katalog; die Extension war komplett auf Deutsch verdrahtet, inklusive `Locale("de_DE")` im Datumsformat. |
| CI | Zweiter Job auf macOS: Framework, die Compose-frei-Prüfung, iOS- und macOS-Build. Testschritte unqualifiziert. |
| Material3 | Der Pin bleibt — nachgemessen: auf der neuesten stabilen Version 45 Compile-Fehler. Der alte Kommentar warnte vor einer Version, die es nicht gibt. |
| Tests umgezogen | Jedes Modul testet sich selbst, `:core:testing` hält die Fakes. 318 / 424, exakt die vier gelöschten weniger. |
| Doku | `PROJECT.md` neu (es beschrieb noch April), `AGENTS.md` nachgezogen, Sonar-Pfade auf alle Module. |

Punkt 1 der P9-Liste — `IntegrationExample.kt` löschen — war schon in P3 erledigt worden und
stand nur noch doppelt im Plan.

---

## 8. Zur Parallelisierung

Der Nutzer hat nach Subagents und Worktrees gefragt. Einschätzung nach neun Phasen:

* **Über Phasen hinweg lohnt es sich nicht.** Die Kette ist bindend, und jede Phase schreibt
  dieselben zentralen Dateien um. P3 hat fast jede Datei im Datenpfad angefasst, P4 die gesamte
  Präsentationsschicht.
* **Innerhalb einer Phase über disjunkte Dateimengen schon.** P7 wäre der Fan-out gewesen; die
  fünf Screens sind dann doch der Reihe nach entstanden, weil sie zusammen keine zwei Stunden
  gebraucht haben, nachdem die Brücke stand. Der Aufwand lag in der Brücke, und die ist
  unteilbar. P8 hätte sauberer zerfallen können — Widget, Background-Task und Keychain berühren
  einander nicht — nur war der Keychain-Teil am Ende zwei Zeilen Entitlement und der
  Background-Teil eine Datei. Die Aufteilung hätte mehr Absprache gekostet als Arbeit gespart.
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
* **Einen neuen Wächter kaputtmachen, bevor man ihm glaubt.** Das Migrations-Gate aus P6 war
  ab dem ersten Lauf grün — das sagt nichts. Erst `APP_DATABASE_VERSION` auf 5 zu setzen (vier von
  fünf Tests fallen) und `DESTRUCTIBLE_SCHEMA_VERSIONS` zu leeren (der fünfte fällt) hat gezeigt,
  dass er greift. Kostet zwei Minuten.
* **Nachsehen, wo generierte Dateien wirklich landen.** Der Room-Schema-Export schrieb seit dem
  Modulschnitt in ein Verzeichnis namens `$projectDir` (escaptes Dollar in `data/build.gradle.kts`);
  `data/schemas/` war eingefroren, und niemandem ist es aufgefallen, weil nichts es gelesen hat.
  Ein Artefakt, das keiner liest, verrottet lautlos.
* **Was der Plan voraussetzt, muss man nachsehen, bevor man darauf baut.** SKIE stand seit dem
  ersten Entwurf im Plan und hätte P7 tragen sollen; zwei Minuten auf Maven Central hätten von
  Anfang an gezeigt, dass es bei Kotlin 2.2.0 aufhört. Der Ersatz von Hand war klein — teuer wäre
  es geworden, das erst nach den fünf Screens zu merken.
* **„Geht nicht" im Handoff ist eine Behauptung mit Verfallsdatum.** Der Demo-Login galt auf dem
  iOS-Simulator als nicht eintippbar; über die Zwischenablage geht er. Bei jedem geerbten „geht
  nicht" lohnt ein zweiter Weg, bevor man die Prüfung darum herum baut.
* **Ein injizierter Teil der Umgebung ist keine injizierte Umgebung.** Der Änderungsmonitor nahm
  eine Uhr als Parameter und fragte für die Woche trotzdem `TimeHelper`. Ein Test konnte damit die
  Stunde setzen, aber nicht die Woche — und lief still gegen ein leeres Fenster, ohne zu scheitern.
  Aufgefallen ist es erst, als sieben neue Tests gleichzeitig „keine Änderungen" meldeten.
* **Ein Test, der grün ist, sagt nicht, wofür er grün ist.** Die beiden Sweep-Tests aus dem
  Monitor-Umbau bestanden, während im Hintergrund drei Wochen geseedet wurden, von denen sie nichts
  erwähnten. Erst die Zahl der Requests in der Zusicherung hat es sichtbar gemacht.
* **Ein Schalter wirkt nicht rückwirkend.** Das Prüfintervall von 15 Minuten auf eine Stunde zu
  ändern hätte für jede bestehende Installation gar nichts getan: WorkManager behält mit `KEEP` das
  Intervall, mit dem der Job einmal eingereiht wurde. Bei jeder Änderung an etwas Persistiertem:
  gilt sie auch für die, die es schon haben?
* **Eine Prüfung, deren beide Seiten dieselben Werte tragen, prüft nichts.** In P8 zeigte das
  Widget in der Galerie „Mathematik 1, HOR-120" — genau das, was auch die Vorschaudaten im
  SwiftUI-Code sagen. Erst ein Wert, den es *nur* in der Datenbank gab, hat gezeigt, dass die
  Extension wirklich liest; und erst ein Wert, der nach dem Beenden der App hineingeschrieben
  wurde, dass sie es ohne die App tut. Vorher sah beides identisch aus.
* **Was der Plan nach Swift schiebt, kann Kotlin oft selbst.** BGTaskScheduler stand seit dem
  ersten Entwurf als „braucht Swift" im Plan, und der Log-Stub trug eine Swift-Anleitung im
  Kommentar. `platform.BackgroundTasks` ist eine Plattform-Bibliothek wie jede andere — die
  Annahme hat die Lücke länger offen gehalten als ihre Beseitigung gedauert hat.
* **Ein Schalter, der das Bauen bequem macht, kann die Prüfung entwerten.** `CODE_SIGNING_ALLOWED=NO`
  im iOS-Gate lässt Entitlements weg; der App-Group-Umzug in P6 lief damit sauber durch und war
  trotzdem nicht das, was geprüft werden sollte. Bei jedem „ohne X geht es auch": prüft man dann
  noch dasselbe?
* **Ein Test-Source-Set, das nicht kompiliert, schweigt genauso wie ein Test ohne Zusicherung.**
  `BackgroundServicesIntegrationTest` stand seit dem Modulschnitt da, ohne je zu laufen — dem
  Source-Set fehlten die Test-Abhängigkeiten, und weil kein Gate es anfasst, hat das niemand
  gemerkt. Der Plan kannte nur den einen `assertTrue(true)` darin und nicht, dass die ganze Datei
  tot war. Bei jedem Verzeichnis, das nach Abdeckung aussieht: einmal nachsehen, ob es überhaupt
  gebaut wird.
* **Ein Kommentar ist eine Behauptung mit Verfallsdatum — auch der eigene.** „Nicht auf stable
  1.10.0 upgraden" stand seit P12 im Buildfile und warnte vor einer Version, die es nie gab. Die
  Begründung war trotzdem richtig, nur aus einem anderen Grund; zwei Minuten Nachmessen haben aus
  einer weitergereichten Behauptung eine geprüfte gemacht. Dasselbe galt für den Aufräumpunkt
  `IntegrationExample.kt`, den P3 längst erledigt hatte.
* **Umziehen deckt auf, was am alten Ort nicht auffallen konnte.** Der Testumzug hat zwei Fehler
  sichtbar gemacht, die keiner Prüfung anzulasten sind: Backtick-Namen mit Komma (Kotlin/Native
  lehnt sie ab — in `:composeApp` nie gemerkt, weil es seit P7 kein Apple-Target hat) und vier
  Module ohne `isReturnDefaultValues`. Beide Male war der Code korrekt für den Ort, an dem er lag,
  und falsch für den, an den er gehörte.
* **Beim Verschieben von Tests ist die Zahl der Beweis.** 322 → 318 und 428 → 424, und die
  Differenz war exakt der Test, der vorher gelöscht wurde. Ohne diese Rechnung wäre ein Modul, das
  seine Tests nach dem Umzug gar nicht mehr ausführt, nicht von einem unterscheidbar gewesen, das
  sie erfolgreich ausführt — beide melden „BUILD SUCCESSFUL".
