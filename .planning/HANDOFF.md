# Handoff — v3-Umbau

> Stand: 2026-08-21 · Phasen P-1 bis P7 abgeschlossen und auf `v3` gemerged, dazu zwei Fixes
> außerhalb der Phasenkette: Desktop-TLS und die Trennung von Geheimnissen und Einstellungen
> Arbeitsverzeichnis sauber · nichts gepusht
> Nächste Phase: **P8 — iOS-Plattformdienste nativ**

Alles Abgeschlossene liegt auf `v3`. P8 zweigt von dort ab:

```bash
git checkout -b phase/p8-ios-services v3
```

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

---

## 4. Verifikation — das Gate

Eine Phase ist erst fertig, wenn alles Folgende grün ist.

### 4.1 Tests

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew \
  :composeApp:testDebugUnitTest :composeApp:desktopTest --rerun-tasks
```

**Sollwerte nach P7:** `testDebugUnitTest` **297**, `desktopTest` **403**, 0 Fehler,
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
iosApp/          SwiftUI-App: RootView + fünf Screens, linkt Shared.framework
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
| SwiftUI-Screens | `iosApp/iosApp/RootView.swift` + `iosApp/iosApp/Screens/` |
| iOS-Farben, Karten | `iosApp/iosApp/Design/Theme.swift` — `Color.brand` und `.tint()` an der Wurzel sind der iOS-Ersatz für die Material-You-Seed-Farbe |
| Wochenraster (iOS) | `iosApp/iosApp/Screens/TimetableGrid.swift` — Spurenlayout für parallele Vorlesungen |
| Semester-Reihenfolge | `domain/…/model/SemesterOrder.kt` — sortiert und gruppiert wird einmal im `GradesStore`, beide UIs lesen `GradesState.sections` |
| iOS-Texte | `iosApp/iosApp/Localizable.xcstrings` (en/de) — getrennt von den Compose-Ressourcen |
| Navigation | `composeApp/…/ui/navigation/Routes.kt`, `DhbwNavHost.kt` |
| Test-Graph | `composeApp/src/commonTest/…/testutil/TestKoin.kt` — `WithTestKoin { }`, `testKoin()` |
| Repository-Fakes | `composeApp/src/commonTest/…/testutil/fakes/FakeRepositories.kt` |
| Store-Test-Helfer | `composeApp/src/commonTest/…/presentation/StoreTestSupport.kt` |
| Graph-Prüfung | `composeApp/src/desktopTest/…/di/KoinGraphTest.kt` — bei neuen Bindungen mitpflegen |
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
| Auf einem **echten** iOS-Gerät ist die App Group `group.de.fampopprol.dhbwhorb` erst nutzbar, wenn sie im Apple-Developer-Portal registriert ist. Fehlt sie, protokolliert die App „App group … unavailable" und legt die DB im Dokumentverzeichnis ab — das Widget bliebe blind. Auf dem Simulator ist das kein Thema. | `DatabaseFactory.ios.kt` | **P7/P8** (nur Portal, kein Code) |
| Die Stores sind Applikations-Singles, nicht im Navigations-Scope. Bewusst so: ein Halter je Navigationseintrag würde bei jedem Tab-Wechsel neu laden — genau das, was P4 abgestellt hat. Deshalb bleibt auch `EnsureLoaded` neben `Load` bestehen. | `PresentationModule.kt`, `GradesStore`, `DocumentsStore` | — |
| Läuft die Sitzung ab, während man eingeloggt im Graphen steht, zeigt die Seite „bitte anmelden" statt zur Login-Wurzel zurückzukehren. Ehrlich, aber nicht schön. | `GradesPage`, `DocumentsPage` | P9 |
| **Im Demo-Modus gibt es keine Noten.** `DualisGradeService` hat als einziger Dienst keinen Demo-Zweig, der Abruf endet in `AppError.SessionExpired`. Gilt auf allen Plattformen, stammt aus der Zeit vor dem Umbau — beim Durchklicken von P7 aufgefallen. | `DualisGradeService.kt` | offen |
| **Prüfungen werden farblich unterschieden, aber fast nie erkannt.** Der Block wird bernsteinfarben, wenn `Lecture.isTest` gesetzt ist — und das setzt `TimetableParser` nur bei `background-color:#FF6666` in der Dualis-Zelle. Einträge, die bloß „Mündliche Prüfung" heißen, tragen die Markierung nicht. Offene Entscheidung des Nutzers: zusätzlich am Titel erkennen (Heuristik im Parser, wirkt auf allen Plattformen) oder erst prüfen, ob die Farbmarkierung heute woanders steht — dafür braucht es das rohe HTML einer Woche mit echter Prüfung. | `TimetableParser.kt:79` | offen, wartet auf den Nutzer |
| **Zwei Dinge aus P7 sind nur durch Tests belegt, nicht am Gerät:** die Semester-Reihenfolge (`SemesterOrderTest`) und das Sichern eines Dokuments in „Dateien". Beides braucht ein Konto mit echten Daten — im Demo-Modus gibt es weder Noten noch Downloads. | `GradesScreen.swift`, `DocumentsScreen.swift` | beim nächsten Gerätetest |
| **VoiceOver auf iOS ist statisch geprüft, nicht durchlaufen.** Jedes Bedienelement trägt ein `Text`-Label, Listenzeilen sind zu einem Element zusammengefasst, Symbole haben `accessibilityLabel` — aber niemand ist mit eingeschaltetem VoiceOver durchgegangen. | `iosApp/iosApp/Screens/` | **P8** |
| **„Tab-Wechsel macht 0 Requests" ist auf iOS ungeprüft.** Im Demo-Modus kann es keine Requests geben, und für eine echte Sitzung fehlt auf dem Simulator ein Konto. Auf Android ist es gemessen. | — | wenn ein Konto verfügbar ist |
| Der iOS-`LectureMonitorScheduler` ist ein reiner Log-Stub — auf iOS gibt es **kein** Background-Monitoring, obwohl die Einstellung es anbietet. Feature-Lücke von vor dem Umbau, keine Regression. | `LectureMonitorScheduler.ios.kt` | **P8** |
| `NotificationDispatcher` hält seinen Android-Context statisch, weil `expect class` keinen plattformspezifischen Konstruktorparameter erlaubt. Wie bei `SecureStorage` in P2 ist die Lösung ein Interface mit je einer Implementierung pro Plattform. Bis dahin: `DualisApplication.onCreate()` **muss** `initialize()` rufen — das zu vergessen hat die App von P2 bis P4 beim Öffnen der Einstellungen abstürzen lassen. | `NotificationDispatcher.android.kt` | P8/P9 |
| Die Repository-Fakes liegen in `composeApp/commonTest/testutil/fakes/`, nicht in einem `:core:testing`. Bewusst so, solange alle Tests in `:composeApp` liegen — beides zusammen umziehen. | `testutil/fakes/` | P9 |
| Tests liegen alle in `:composeApp`, nicht in ihren Modulen. Die Gates bleiben dadurch unverändert; der Umzug braucht pro Modul eigene Test-Abhängigkeiten. | `composeApp/src/commonTest` | P9 |
| `composeApp/commonTest/…/data/database/DatabaseFactoryTest.kt` besteht aus vier Tests, die nur prüfen, dass `::createRoomDatabase` nicht null ist. Sie testen nichts. Seit P6 gibt es mit `AppDatabaseMigrationTest` echte Abdeckung derselben Stelle. | `DatabaseFactoryTest.kt` | P9 |
| Der Widget-UseCase liefert bei einem Lesefehler des Caches eine leere Liste statt eines Fehlers — ein Widget hat keine Fehlerdarstellung. Bewusst so, im Code begründet. | `WidgetTimetableUseCase.kt` | — |
| 48 × `catch (e: Exception)` übrig (von ursprünglich 69). Drei Sorten, alle bewusst: die Parser (schlucken eine kaputte Zeile, nicht die Seite), die Klassifikationsstellen selbst (`toAppError`, DB-Zugriffe in den Repositories), und die Plattformschicht (FileViewer, Dispatcher, Scheduler, DNS, SecureStorage). Im Dualis-Datenpfad ist keines mehr. | `:data`, `:services`, `:composeApp` | — |

---

## 7. Nächster Schritt: P8 — iOS-Plattformdienste nativ

Der Plan beschreibt P8 in §2 (Größe L). Drei Stücke, die unabhängig voneinander sind:

1. **WidgetKit ohne Umweg.** Seit P6 liegt die Datenbank im App-Group-Container, seit P7 linkt die
   App `Shared.framework` — die Voraussetzungen für den direkten Weg stehen beide. Heute schreibt
   `SharedApp` noch bei jeder DB-Änderung eine JSON-Momentaufnahme über `WidgetDataWriter` in
   `NSUserDefaults`, und `TimetableWidget.swift` hält eine **von Hand gepflegte Swift-Kopie** der
   Kotlin-DTOs. Jede Feldänderung bricht das Widget still. Ersatz: die Extension linkt
   `Shared.framework` und ruft den Widget-UseCase direkt; `WidgetDataWriter`, der JSON-Umweg und
   die Notification-Brücke in `RootView` entfallen zusammen.
2. **BGTaskScheduler + UNUserNotificationCenter.** `LectureMonitorScheduler.ios.kt` ist ein
   Log-Stub; die Einstellung „Benachrichtigung für Vorlesungsänderungen" verspricht auf iOS etwas,
   das nicht passiert. Der Hinweistext unter dem Schalter sagt das derzeit offen — mit P8 gehört
   er wieder entfernt.
3. **Keychain-Access-Group**, damit Widget und App dieselben Zugangsdaten sehen.

**Was aus P7 hier ankommt:**

* Der Weg an den Login vorbei ist geklärt (§3, Zwischenablage) — Screens hinter dem Login lassen
  sich von Hand prüfen.
* `CODE_SIGNING_ALLOWED=NO` weglassen, sobald etwas an einem Entitlement hängt. In P8 hängt fast
  alles daran (App Group, Keychain-Gruppe, Background-Task).
* Der VoiceOver-Durchlauf steht noch aus (§6) und passt gut in dieselbe Sitzung, weil er dieselbe
  Gerätearbeit ist.

## 8. Zur Parallelisierung

Der Nutzer hat nach Subagents und Worktrees gefragt. Einschätzung nach sieben Phasen:

* **Über Phasen hinweg lohnt es sich nicht.** Die Kette ist bindend, und jede Phase schreibt
  dieselben zentralen Dateien um. P3 hat fast jede Datei im Datenpfad angefasst, P4 die gesamte
  Präsentationsschicht.
* **Innerhalb einer Phase über disjunkte Dateimengen schon.** P7 wäre der Fan-out gewesen; die
  fünf Screens sind dann doch der Reihe nach entstanden, weil sie zusammen keine zwei Stunden
  gebraucht haben, nachdem die Brücke stand. Der Aufwand lag in der Brücke, und die ist
  unteilbar. P8 zerfällt sauberer: Widget, Background-Task und Keychain berühren einander nicht.
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
* **Ein Schalter, der das Bauen bequem macht, kann die Prüfung entwerten.** `CODE_SIGNING_ALLOWED=NO`
  im iOS-Gate lässt Entitlements weg; der App-Group-Umzug in P6 lief damit sauber durch und war
  trotzdem nicht das, was geprüft werden sollte. Bei jedem „ohne X geht es auch": prüft man dann
  noch dasselbe?
