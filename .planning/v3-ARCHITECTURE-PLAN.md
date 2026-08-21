# v3 Architektur-Umbauplan — Shared Store (MVI/UDF) + natives iOS

> Status: Entwurf · Erstellt: 2026-08-20 · Basis: v2.1.1 (`a84746f`)
> Branch-Modell: Integration auf `v3`, je Phase ein `phase/pN-…`-Branch (Git erlaubt `v3` und
> `v3/…` nicht gleichzeitig als Ref).
> Entscheidungen: Architektur **B (Shared Store / MVI-UDF)** · Migration **inkrementell, Feature für Feature**
> · iOS **vollumfänglich nativ inkl. Widget & Notifications** · Bausteine: **Koin, SKIE, Navigation-Library, Room-Migrationen**

---

## 1. Zielbild

### 1.1 Modulgraph

```
:core:common        Outcome/AppError, Clock, DispatcherProvider, Logging-Setup
:core:testing       Fakes, Fixture-Loader, Test-Dispatcher  (nur testImplementation)

:domain             Modelle, Repository-Interfaces, UseCases      ← keine Frameworks
:data:dualis        Ktor, DualisApiClient, Parser, Remote-DTOs
:data:local         Room, DAOs, Entities, Migrationen, SecureStorage (expect/actual)
:data:di            Repository-Impls + Koin-Module (bindet :data:* an :domain)

:presentation       MVI-Stores: State / Intent / Msg / Effect / Reducer  ← keine Compose-Runtime

:composeApp         Compose-UI + Navigation (Android, Desktop) + Platform-Entrypoints
:shared             Umbrella-Framework für Apple: :domain + :presentation + :data:di + initKoin()
```

Abhängigkeitsrichtung ist strikt einseitig: `:composeApp`/`:shared` → `:presentation` → `:domain` ← `:data:*`.
`:domain` kennt weder Ktor noch Room noch Compose. Das ist die Bedingung dafür, dass Swift
`:shared` einbinden kann, ohne Compose mitzuschleppen.

### 1.2 Datenfluss

```
SwiftUI View ──┐                                   ┌── Compose Screen
               ├─▶ Store.dispatch(Intent) ─▶ Reducer (pur) ─▶ State ─▶ StateFlow ─┤
               │                    │                                             │
               │                    └─▶ EffectHandler (suspend) ─▶ UseCase ─▶ Repository
               │                                    │                    ├─ Remote (Dualis)
               └◀── effects: Flow<Effect> ◀─────────┘                    └─ Local  (Room)
```

* **Intent** — von außen: `Refresh`, `WeekSelected(offset)`, `LoginSubmitted(user, pass)`
* **Msg** — intern, Ergebnis eines Effects: `LecturesLoaded(list)`, `LoadFailed(AppError)`
* **State** — immutable data class, eine pro Feature, vollständig serialisierbar
* **Effect** — One-Shot: `NavigateTo(...)`, `ShowSnackbar(...)`, `RequestNotificationPermission`

Regel: `reduce(State, Msg): State` ist pur und synchron — keine Coroutinen, kein I/O, kein Zufall.
Alles Nichtdeterministische lebt im EffectHandler. Damit sind die heutigen Race-Conditions
(`getDataWithRetry`, 500ms-Init-Timeout, `isReAuthenticating`-Flag) strukturell nicht mehr formulierbar.

### 1.3 Vertrag (`:presentation`)

```kotlin
interface Store<S : Any, I : Any, E : Any> {
    val state: StateFlow<S>
    val effects: Flow<E>
    fun dispatch(intent: I)
    fun close()
}

abstract class BaseStore<S : Any, I : Any, M : Any, E : Any>(
    initialState: S,
    private val scope: CoroutineScope,
) : Store<S, I, E> {
    protected abstract fun reduce(state: S, msg: M): S
    protected abstract suspend fun EffectScope<M, E>.handle(intent: I, state: S)
}
```

`EffectScope` erlaubt genau zwei Dinge: `emit(msg)` (→ Reducer) und `send(effect)` (→ One-Shot).
Kein direkter State-Zugriff aus dem Effect-Handler.

### 1.4 Fehlermodell (`:core:common`)

```kotlin
sealed interface AppError {
    data object Offline : AppError
    data object SessionExpired : AppError
    data class Http(val code: Int) : AppError
    data class Parse(val source: String, val hint: String) : AppError
    data class Storage(val hint: String) : AppError
    data class Unexpected(val hint: String) : AppError
}

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val error: AppError) : Outcome<Nothing>
}
```

Eigene Sealed-Hierarchie statt `kotlin.Result`, weil SKIE Sealed Classes in echte Swift-Enums
übersetzt — `switch error { case .offline: … }` ohne Casting. Ersetzt die heutigen 75 `catch (e: Exception)`,
die nach `null`/`emptyList()` degradieren und der UI die Ursache verschweigen.

---

## 2. Phasenplan

Jede Phase ist einzeln mergebar und lässt v2.x releasefähig. Reihenfolge ist bindend —
P1 ohne P0 heißt, Parser-Regressionen unbemerkt zu verschieben.

### P-1 — Grüner Baseline-Build · Größe S · **abgeschlossen**

Voraussetzung für alles Weitere: ohne grünen Ausgangszustand kann kein automatisierter Umbau
zwischen „ich habe etwas kaputtgemacht" und „das war schon vorher kaputt" unterscheiden.

Ausgangslage auf `main` (a84746f): `testDebugUnitTest` und `desktopTest` kompilierten nicht
(8 Fehler), danach 19 fehlschlagende Tests. Beide Tasks laufen bei jedem Push in
`.github/workflows/ci.yml` — die CI war entsprechend rot.

Behoben:
* `TimetableUiState.isLoading` existiert nicht mehr (heute `isLoadingWeeks: Set<Int>`) —
  `TimeoutFallbackTest` und `ViewModelCleanupTest` nachgezogen.
* `TimetablePageTest` benutzte ein anonymes `object` plus `as? TimetableViewModel`, was immer
  `null` ergab: die Tests rendern eine leere Seite und prüften nichts. Kompilierbar gemacht und
  mit Begründung `@Ignore`t — reaktiviert in P4, wenn der Store injizierbaren State liefert.
* **Locale-Abhängigkeit:** UI-Tests suchten nach `"Timetable"`, während die App auf einer
  deutschen JVM `"Stundenplan"` rendert. Auf englischen CI-Runnern grün, auf deutscher
  Entwicklungsmaschine rot. Umgestellt auf stabile Test-Tags (`navItemTestTag`,
  `themeButtonTestTag`), inklusive `DesignSelectionCard`, dessen Tag selbst aus dem
  lokalisierten Label gebaut war.
* **Echter Bug:** Der Logout-Button wurde auch im ausgeloggten Zustand gerendert
  (`HelpSelectionCard` hing nicht am `isLoggedIn`-Flag). Der Test hatte recht, der Code unrecht —
  Button ist jetzt an `showLogout` gebunden.
* `validateFields_specialCharactersInUsername_fails` benutzte `test.user@hb.dhbw-stuttgart.de`
  als Negativbeispiel — das ist das reguläre DHBW-Format und wird korrekt akzeptiert. Test auf
  ein tatsächlich ungültiges Zeichen (`+`) korrigiert, Positivtest für Punkt-Adressen ergänzt.
* `testDocumentsViewModelHandlesNullServiceInitially` las `uiState.value` ohne Collector — bei
  `stateIn(WhileSubscribed)` immer der Default. Mit Collector und expliziter virtueller Zeit
  korrigiert (`advanceUntilIdle()` treibt `backgroundScope`-Delays nicht).
* `ThemeJvmTest` behauptete, Desktop nutze die statischen Farbschemata. Tatsächlich erzeugt
  `Theme.desktop.kt` das Schema per MaterialKolor aus dem Seed. Tests prüfen jetzt das
  tatsächliche Verhalten (Seed steuert das Schema, Dark ist dunkler als Light).
* `Phase8StabilityTest` rendert Compose-UI und lief ohne Android-Runtime — analog zu `AppTest`
  aus den Android-Unit-Tests ausgeschlossen, läuft weiter unter `desktopTest`.

**Ergebnis:** `testDebugUnitTest` 168 Tests, `desktopTest` 279 Tests, 0 Fehler, 4 dokumentiert
übersprungen.

**Nebenbefund, nicht behoben:** `useMaterialYou` hat auf Desktop keine Wirkung —
`getColorScheme` ignoriert das Flag und erzeugt immer ein Seed-Schema. Die Einstellung ist dort
also folgenlos. Behandeln in P5.

### P0 — Sicherheitsnetz für die Parser · Größe S · **abgeschlossen**

Die Regex-Parser sind die einzige Schicht ohne typisierten Vertrag und werden in jeder späteren
Phase angefasst. Vor dem ersten Verschieben abgesichert.

* `commonTest/.../parser/fixtures/DualisFixtures.kt` — Fixtures als Kotlin-Konstanten statt
  Ressourcendateien: Resource-Loading ist über KMP-Targets hinweg umständlich (Native hat keinen
  Classloader), ein String funktioniert überall.
* Herkunft ist im Fixture-KDoc vermerkt. `Documents.LIST` stammt aus einem echten Capture
  (`.planning/example/documents.html`), alles andere ist aus den Parser-KDocs abgeleitet, die
  ihrerseits aus Captures stammen. **Ein abgeleitetes Fixture beweist, dass der Parser die Struktur
  verarbeitet, die wir für Dualis' Ausgabe halten — nicht, dass diese Annahme noch stimmt.**
  Wenn ein Parser in Produktion bricht, gehört das echte Capture ins Fixture, nicht ein
  nachgezogener Regex.
* Neue Contract-Tests: `GradeParserTest` (13), `TimetableParserTest` (18),
  `SessionExpiredContractTest` (8) für `HtmlParser`, `AuthParser`, `DocumentParser`.
  `GradeParser` und `TimetableParser` — die beiden größten Parser — hatten zuvor **keinen
  einzigen Test**.
* Degradations-Fälle für jeden Parser: leeres HTML, Nicht-HTML, Session-Expired-Seite,
  Woche ohne Vorlesungen, Wochenraster ohne Datumsköpfe, leere Notentabelle.

**Ergebnis:** `testDebugUnitTest` 208 Tests, `desktopTest` 319 Tests, 0 Fehler (vorher 168 / 279).

**Gefundener Defekt, dokumentiert statt behoben:** `TimetableParser.extractWeekDates()` nimmt das
Jahr aus `Clock.System.now()`, weil der Dualis-Kopf nur `Mo 05.01.` ohne Jahr liefert. Der Pager
erlaubt ±1000 Wochen — jede Woche außerhalb des laufenden Kalenderjahres bekommt damit das falsche
Jahr, und eine Woche über den Jahreswechsel bekommt für beide Hälften dasselbe. Die Korrektur
braucht den angefragten Datumsbereich, den der Parser nicht kennt; sie gehört in die
Repository-Schicht. Als `@Ignore`-Test in `TimetableParserTest` festgehalten, **fällig in P3**.

### P1 — Modulschnitt · Größe L · **abgeschlossen**

Der Package-Root bleibt in jedem Modul `de.fampopprol.dhbwhorb`, Modulgrenzen ändern keine
Package-Namen — deshalb war kein einziger Import anzupassen.

```
:core:common    11 Dateien   Platform-Erkennung, AndroidAppContext
:domain         11 Dateien   Dualis-Modelle, TimeHelper
:data           55 Dateien   Ktor, Parser, Dualis-Services, Room + DAOs, SecureStorage, Prefs
:services       30 Dateien   LectureService, Notifications, Widget-UseCases, FileViewer
:presentation    6 Dateien   ViewModels
:shared          1 Datei     Umbrella -> Shared.framework für Apple
:composeApp     52 Dateien   Compose-UI, Navigation, Entry Points, Glance-Widget
```

**Abnahme erfüllt:** `Shared.framework` (iosSimulatorArm64) enthält **0 Compose-Symbole** bei
**72 MB** — gegenüber 368 MB und 50.066 Compose-Symbolen im bestehenden `ComposeApp.framework`.
Alle Targets kompilieren (Android, Desktop, iosArm64, iosSimulatorArm64, macosArm64, macosX64),
beide Gates grün: 209 / 320 Tests, 0 Fehler.

`:presentation` ist bewusst **nicht** Teil von `Shared.framework`. Ein erster Versuch mit
Presentation im Framework ergab 19.504 Compose-Symbole und 180 MB, weil die ViewModels über
`mutableStateOf` und `lifecycle-viewmodel-compose` an der Compose-Runtime hängen. Das Modul kommt
in P4 dazu, sobald die Stores `StateFlow` liefern.

**Drei Zyklen, die der Schnitt sichtbar gemacht hat.** Alle drei sind derselbe Befund: Zugriff auf
App-Ressourcen wird heute über statische Halter gelöst, und die stehen im App-Modul.

1. `SecureStorage.android` las `DualisApplication.appContext` → hätte `:data → :composeApp`
   bedeutet. Der Context-Halter ist als `AndroidAppContext` nach `:core:common` gewandert,
   `DualisApplication` befüllt ihn in `onCreate()`.
2. `LectureMonitorScheduler.android` rief `WidgetSyncWorker` direkt auf, der Glance braucht und
   deshalb in `:composeApp` bleiben muss. Umgedreht über `WidgetRefreshTrigger`, den
   `DualisApplication` bedient — `Application.onCreate()` läuft immer vor jedem Worker im Prozess.
3. `FileViewer.android` holt seinen Context aus `NotificationDispatcher.getContext()`. Deshalb
   liegt `FileViewer` jetzt in `:services` statt in `:core:common` — dort, wo seine
   Abhängigkeiten sind.

Beide neuen Halter sind Service Locators und teilen deren Probleme. **P2 ersetzt sie durch
Koin-Bereitstellung** — sie sind ausdrücklich Übergangslösungen, keine Zielarchitektur.

**Coverage-Report repariert.** Nach dem Schnitt deckte `koverXmlReportKmpCoverage` nur noch
`:composeApp` ab (18 Pakete, ohne Parser und Data) — die CI hätte weiter grün gemeldet, während
SonarCloud den größten Teil des Codes nicht mehr sieht. Kover ist jetzt in allen Modulen aktiv und
wird über `kover(projects.…)` in `:composeApp` aggregiert: 49 Pakete, 33,7 % Instruction-Coverage.

**Der iOS-Build war schon vor P1 kaputt** und ist jetzt repariert. Auf `v3` wie auf `main`
scheiterte `xcodebuild` an `import composeApp` (klein geschrieben, das Framework heißt
`ComposeApp`); danach an `UIRootViewControllerHelper.getViewController`, denn ein Kotlin-`object`
erreicht Swift als `.shared`. Zwei Einzeiler in `iOSApp.swift`. Ohne sie ist auf iOS überhaupt
nichts verifizierbar, deshalb sind sie trotz der Regel „nur verschieben" Teil dieser Phase.
Der Simulator-Build läuft jetzt durch.

**Tests sind bewusst in `:composeApp` geblieben.** Sie decken über die Modulgrenzen hinweg weiter
alles ab, weil `:composeApp` von allen Modulen abhängt, und die CI-Gates bleiben unverändert. Ein
Umzug der Tests in ihre Module braucht pro Modul eigene Test-Abhängigkeiten und gehört zu P3/P4,
wenn die Modulverträge stehen. Zwei Test-Abhängigkeiten (`sqlite-bundled`, `ktor-client-core`)
mussten `:composeApp` explizit bekommen, weil die Tests Room und Ktor direkt benutzen.

**Kleinere Anpassungen:** Zwei Smart Casts in `GradesPage` durch lokale `val`s ersetzt — Smart
Casts greifen nicht über Modulgrenzen. `ic_school.xml` nach `:services` verschoben, weil
`NotificationDispatcher` es referenziert und `R` modul-lokal aufgelöst wird.

### P2 — Koin: ein Composition Root statt drei · Größe M · **abgeschlossen**

Die Phase mit dem größten Stabilitätsgewinn pro Zeile.

```
MainActivity      416 -> 112 Zeilen
App.kt            443 -> 208 Zeilen, 10 Parameter -> 0
desktopMain/main   208 ->  66 Zeilen
MainViewController 141 ->  81 Zeilen
```

**Entfernt:**
* `getDataWithRetry` in allen drei ViewModels — 5 Versuche × 1 s Polling, weil Services nullable
  waren. Services sind jetzt garantiert vorhanden, das Warten entfällt ersatzlos.
* der 500-ms-`timeoutJob` in `MainActivity`, der die UI vor den Services rendern ließ
* `NotificationServiceLocator`, `WidgetServiceLocator`, `WidgetRefreshTrigger` und
  `AndroidAppContext` — alle vier durch Koin ersetzt. Die letzten beiden hatte P1 selbst als
  Übergangslösung eingeführt.
* die Parameter `testAuthenticationService`, `testCredentialsProvider`, `testSecureStorage`,
  `sessionManager`, `sharedHttpClient`, `database`, `notificationPreferencesInteractor`,
  `isInitialized`, `databaseErrorMessage`, `timetableViewModel` aus `App()`
* die Inline-Service-Konstruktion samt `DisposableEffect { onDispose { cleanup() } }` in
  `GradesPage` und `DocumentsPage` — **damit lädt ein Tab-Wechsel nicht mehr neu**
* der Bootstrap-Block in `WidgetSyncWorker`, der den Locator bei Cold Start nachbaute

**`SecureStorage` ist keine expect/actual-Klasse mehr.** Eine expect-Klasse kann keinen
plattformspezifischen Konstruktorparameter haben — Android braucht aber einen Context. Statt des
statischen Context-Halters gibt es jetzt vier Implementierungen von `SecureStorageInterface`,
gebunden in `dataPlatformModule()`. `SecureStorageWrapper` entfällt. Der Desktop-Preferences-Knoten
bleibt identisch (`userNodeForPackage` schlüsselt aufs Package, nicht auf die Klasse), also
**verlieren bestehende Desktop-Nutzer ihre gespeicherten Werte nicht**.

**Logout ist aus der UI heraus.** `LogoutUseCase` in `:services` fasst Session beenden, Zugangsdaten
löschen und Cache leeren zusammen — die drei gehören zusammen, sonst sieht der nächste Nutzer die
Daten des vorherigen. Damit braucht die UI-Schicht kein `AppDatabase` mehr.

**Ein echter Laufzeitfehler, den kein Compile-Check gefunden hätte.** Auf iOS startete Koin zuerst
in einem `LaunchedEffect` — der läuft aber *nach* der Komposition, während `App()` seine
Abhängigkeiten *während* der Komposition anfordert. Die App stürzte beim ersten Frame mit
`KoinApplication has not been started` ab. `MainViewController()` startet den Graphen jetzt
synchron, bevor der Controller irgendetwas komponiert — analog zu `Application.onCreate()` und
`main()`. Der Start ist idempotent, weil Swift den Hosting-Controller mehrfach erzeugen kann.

**Verifikation.** Für eine DI-Umstellung reicht ein grüner Compile nicht: eine fehlende Bindung ist
kein Compile-Fehler, sondern ein Absturz beim ersten Öffnen des betroffenen Screens.
* `KoinGraphTest` prüft jedes Modul mit Koins `verify()` (Konstruktorparameter gegen Bindungen,
  ohne zu instanziieren) **und** baut in `graph_actuallyBuilds` den echten Graphen auf — echte
  Room-DB, echter Keyring, echter Ktor-Client — inklusive Zusicherung, dass der `HttpClient` ein
  Singleton ist, sonst geht das Session-Cookie nach dem Login verloren.
* `ViewModelResolutionTest` löst alle drei Screen-ViewModels gegen den Mock-Graphen auf. Bewusst
  nicht gegen den echten: der würde die im Keychain liegenden Zugangsdaten benutzen und könnte
  einen echten Dualis-Request in die Testsuite bringen.
* iOS im Simulator gestartet: Login-Screen rendert, Log zeigt sauberen Graph-Start.
* Android auf dem Emulator gestartet: `DualisApplication initialised`, Stundenplan mit gültiger
  Session, Navigation intakt, keine Koin-Fehler im Logcat.

`verify()` meldete zwei Falschpositive (inline übergebene Ktor-Engine und die `() -> Service`-
Lambdas) — als `extraTypes` deklariert. Der dritte Treffer war echt: `WidgetLectureRepository` hatte
keine Bindung, nur die konkrete Klasse. Jetzt gebunden, und zwar auf die DB-only-Implementierung,
damit ein Hintergrund-Refresh nie eine Session oder das Netz braucht.

**Ergebnis:** `testDebugUnitTest` 211 Tests, `desktopTest` 326 Tests, 0 Fehler.
Coverage 33,7 % → 37,5 %. Alle Targets kompilieren, iOS- und Android-Build laufen auf dem Gerät.

**Zwei Tests entfallen, weil ihr Gegenstand weg ist:** `TimeoutFallbackTest` prüfte die
Retry-Schleife, `Phase8StabilityTest.app_displaysLoadingIndicator_whenNotInitialized` den
Initialisierungs-Zwischenzustand. Beides existiert nicht mehr.

### P3 — Domain, Repositories, Fehlermodell · Größe L · **abgeschlossen**

* `Outcome`/`AppError` aus §1.4 in `:core:common`.
* Repository-Interfaces in `:domain`: `AuthRepository`, `SessionRepository`, `TimetableRepository`,
  `GradeRepository`, `DocumentRepository`, `PreferencesRepository`.
* Impls in `:data:di` — sie kapseln die bisherigen `DualisXService`-Klassen plus DAO-Zugriff.
* `LectureService` wird aufgeteilt: Cache-First-Orchestrierung → `TimetableRepositoryImpl`,
  Widget-Zugriff → eigener `WidgetSnapshotUseCase` in `:domain`. Die Doppelrolle
  „Service ist gleichzeitig `WidgetLectureRepository`" entfällt.
* UseCases mit einer Aufgabe: `GetWeekTimetable`, `RefreshTimetable`, `GetGradesForSemester`,
  `ComputeGpa`, `LoginWithCredentials`, `RestoreSession`, `ListDocuments`, `DownloadDocument`.
* Jeder `catch (e: Exception)` wird zu einem `Outcome.Err(AppError.…)` mit konkretem Fall.
  Insbesondere: HTTP-Fehler ≠ Parse-Fehler ≠ Session abgelaufen ≠ offline.
* Session-Handling: `isReAuthenticating: Boolean` in `SessionManager` durch eine `Mutex` plus
  Single-Flight (`Deferred`-Dedupe) ersetzen, damit parallele 401-Antworten genau einen Re-Login auslösen.

**Fertig wenn:** kein UseCase gibt `null` oder `emptyList()` als Fehlerkanal zurück; die
Parser-Fixtures aus P0 laufen unverändert gegen die neuen Repository-Impls.

**Was tatsächlich passiert ist** (`phase/p3-domain`):

* `Outcome`/`AppError` liegen in `:core:common`. Gegenüber §1.4 sind drei Fälle dazugekommen, weil
  sie im Code auseinanderfielen: `InvalidCredentials` (Passwort falsch — erneut versuchen hilft
  nicht) getrennt von `SessionExpired` (Token abgelaufen — Re-Login hilft), `NoCredentials`
  (nichts gespeichert — Login-Screen statt Fehlermeldung) und `Unsupported` (Download im
  Demo-Modus).
* Die sechs Repository-Interfaces liegen in `:domain`, die Implementierungen in `:data`
  (nicht `:data:di` — der Modulschnitt aus P1 hat kein `:data:di`, und ein eigenes Modul nur für
  fünf Klassen wäre Aufwand ohne Gegenwert). Dazu Domänenmodelle `Lecture`, `GradeEntry`,
  `Semester`, `Session`, `TimetableWeek`, damit `:domain` frei von Room-Annotationen bleibt.
* `DualisPageGateway` ist neu und im Plan nicht vorgesehen gewesen: die drei `DualisXService`
  trugen je eine eigene Kopie der Schleife „holen, Seite prüfen, neu anmelden, nochmal". Die
  Kopien waren auseinandergelaufen. Eine Implementierung, eine Klassifikation.
* `LectureService` ist aufgelöst. Cache-First → `TimetableRepositoryImpl`, Widget-Zugriff →
  `TimetableRepository.getCachedLectures()`, das nie ins Netz geht. `WidgetLectureRepository` und
  `DatabaseWidgetRepository` entfallen damit, ebenso die Doppelrolle. `LogoutUseCase` ist in
  `AuthRepositoryImpl.logout()` plus den `Logout`-UseCase übergegangen.
* Nebenbei gefunden und behoben: der Staged-Load holte die Woche **zweimal** — er startete einen
  Hintergrund-Fetch und begann sofort einen zweiten identischen, weil der Cache noch leer war.
  `TimetableRepositoryImpl` hält pro Woche ein `Deferred`, dem sich der zweite Aufruf anschließt.
* Re-Auth-Single-Flight sitzt in `ReAuthenticator` (Mutex + `CompletableDeferred`).
  `SessionManager.isReAuthenticating` ist weg. Vier Tests decken das ab, darunter der Fall, den
  das Boolean falsch behandelte: drei gleichzeitige Aufrufe ergeben genau einen Login.
* Der Jahres-Defekt aus P0 ist behoben: `parseWeeklyView(html, weekStart)` bekommt die angefragte
  Woche und wählt das Jahr **pro Tag** — eine Woche über den Jahreswechsel bekommt sonst fünf
  Tage mit demselben Jahr. Der `@Ignore` ist raus, zwei echte Tests stehen an seiner Stelle.
* `catch (e: Exception)`: 69 → 48. Übrig sind drei Sorten, alle bewusst: die Parser (schlucken
  eine kaputte Zeile, nicht die Seite — die Seitenprüfung fängt echte Brüche), die
  Klassifikationsstellen selbst (`toAppError`, DB-Zugriffe in den Repositories), und die
  Plattformschicht (FileViewer, NotificationDispatcher, Scheduler, WidgetSyncWorker, DNS,
  SecureStorage). Im Dualis-Datenpfad ist keines mehr.
* Mitgenommen, weil P3 sie ohnehin anfassen musste: `services/notifications/IntegrationExample.kt`
  (213 Zeilen toter Beispielcode, stand für P9), `HttpClientInitializer` (von nichts benutzt),
  `AuthenticationService.createSharedHttpClient` (der Client kommt seit P2 aus Koin), zwei
  ungenutzte private Methoden in `LectureChangeMonitor`, und tote `viewModel == null`-Zweige in
  `DocumentsPage`/`GradesPage`.

**Gate bei Abschluss:** `testDebugUnitTest` 215 Tests, `desktopTest` 330 Tests, 0 Fehler,
0 bzw. 4 übersprungen (die vier `TimetablePageTest`, die P4 braucht). Framework Compose-frei (0),
iOS-Build grün, beide Apps gestartet und mit echter Sitzung durch Stundenplan, Noten und
Dokumente geprüft.

### P4 — MVI-Stores · Größe L · **abgeschlossen**

Pro Feature ein Store in `:presentation`, jeweils `<Feature>State/Intent/Msg/Effect/Store`.

| Store | ersetzt | Besonderheit |
|---|---|---|
| `AuthStore` | `LoginFormViewModel` + Login-Zweige in `App.kt` | Demo-Mode als expliziter State |
| `TimetableStore` | `TimetableViewModel` | Wochen-Paging als `Map<Int, WeekState>`; Single-Flight je Woche liegt seit P3 im Repository |
| `GradesStore` | `GradesViewModel` | Semesterauswahl + `ALL_SEMESTERS` als Sealed statt Magic-String |
| `DocumentsStore` | `DocumentsViewModel` | Download-Fortschritt als Effect |
| `SettingsStore` | Preference-Callbacks in `App.kt` | Theme + Notifications, ein State |
| `AppStore` | Navigations-`when` in `App.kt` | Session-Status, Root-Routing |

* `mutableStateOf` verschwindet aus der Präsentationsschicht — `:presentation` hat **keine**
  Compose-Runtime-Abhängigkeit mehr. Nur noch `StateFlow<State>`.
* Die heutige Doppelhaltung in `GradesViewModel` (`uiState` **und** `_isLoading`/`_data`/`_isRefreshing`)
  entfällt; Ladezustände sind Felder des einen States.
* Reducer-Tests: reine Funktionen, ein Test pro Intent → Msg → State-Übergang, ohne Coroutinen.
* Effect-Handler-Tests gegen Fake-Repositories aus `:core:testing`.
* Store-Lebensdauer: Android/Desktop über einen `androidx.lifecycle.ViewModel`-Halter im
  Navigations-Scope, iOS über `@StateObject` im jeweiligen SwiftUI-View-Baum.

**Fertig wenn:** jeder Reducer ist pur (Test läuft ohne `runTest`); Tab-Wechsel löst keinen
Reload aus, weil der Store nicht mehr im `remember` der Page hängt.

**Was tatsächlich passiert ist** (`phase/p4-stores`):

* Der Vertrag aus §1.3 steht in `:presentation/store`. Zwei Abweichungen, beide bewusst:
  `BaseStore` bekam ein `dedupeKey(intent)` — ohne das müsste jeder Store „läuft schon" mit einem
  Boolean im State lösen, und genau das ist die Form, die zwei Coroutinen beide als `false` lesen
  können. Und der **Reducer ist eine Top-Level-Funktion**, keine Methode: `reduceTimetable(state,
  msg)` kann kein Repository, keinen Scope und keine Uhr erreichen, die Reinheit ist damit
  strukturell statt versprochen. Die Reducer-Tests rufen sie direkt auf — ohne `runTest`, ohne
  Dispatcher, ohne Fake.
* Sechs Stores wie geplant. `mutableStateOf` ist aus `:presentation` verschwunden, die
  Compose-Plugins sind aus `presentation/build.gradle.kts` entfernt, und `:presentation` ist in
  `Shared.framework` exportiert: **0 Compose-Symbole, 24 Store-Typen im Swift-Header.** Damit ist
  die Voraussetzung für P7 erfüllt.
* `LectureModel` ist entfallen — es war eine Kopie von `domain.model.Lecture` mit anderen
  Feldnamen. Die UI arbeitet direkt auf dem Domänenmodell, was auch P7 zugutekommt.
* Die Validierung im `AuthStore` liefert Enums (`UsernameError`, `PasswordError`) statt Strings.
  `LoginFormViewModel.validateFields()` bekam drei lokalisierte Meldungen als Parameter, war also
  nur aus einer Composable heraus aufrufbar und nur gegen englischen Text testbar.
* `WeekLabelData` ist entfallen: das Label wird aus `TimetableWeek.start`/`end` abgeleitet, die der
  Store vom Repository bekommt — statt aus einer zweiten, eigenen Datumsrechnung gegen die Uhr.
* **Nicht im Plan und beim Durchklicken gefunden:** `EnsureLoaded`. Ein Screen betritt bei jedem
  Tab-Wechsel die Composition neu, also feuerte sein `LaunchedEffect` erneut `Load` — der Store
  überlebte den Wechsel, die Seite lud trotzdem nach. Das Abnahmekriterium war also mit
  „Store ist ein Single" allein *nicht* erfüllt. `EnsureLoaded` lädt nur, wenn noch nichts geladen
  wurde; `Load` bleibt die Retry-Aktion. Dafür brauchte es `hasLoaded` in `WeekState`,
  `GradesState` und `DocumentsState` — ohne das ist eine leere Woche (Semesterferien) nicht von
  einer ungeladenen zu unterscheiden. **Nachgemessen auf dem Gerät: 0 Requests bei einem kompletten
  Tab-Durchlauf.**
* **P2-Regression gefunden und behoben:** P2 hat `NotificationDispatcher.initialize(this)` aus
  `MainActivity` entfernt und nicht ersetzt. Seitdem stürzte die App auf Android beim Öffnen der
  Einstellungen ab (`IllegalStateException: NotificationDispatcher not initialized`). Kein Test
  konnte das sehen: die Compose-UI-Tests laufen auf Desktop, und P2/P3 haben die App zwar
  gestartet, aber die Einstellungen nie geöffnet. Behoben an beiden Enden — Aufruf zurück in
  `DualisApplication.onCreate()`, und `hasPermission()` meldet ohne Context „keine Berechtigung"
  statt zu werfen; eine Berechtigungsabfrage darf die App nicht abschießen.
* Die vier `@Ignore`-Tests in `TimetablePageTest` sind aktiv. Der Seite einen Store zu übergeben
  ist alles, was ihnen gefehlt hat. Die Navigations-Assertions laufen über Test-Tags, nicht über
  die Labels — die kommen aus String-Ressourcen, und diese JVM läuft auf `de_DE`.

**Gate bei Abschluss:** `testDebugUnitTest` 289 Tests, `desktopTest` 380 Tests, 0 Fehler,
**0 übersprungen** (erstmals im Umbau). Framework Compose-frei (0), iOS-Build grün, beide Apps
gestartet und durchgeklickt: Stundenplan, Noten, Dokumente, Einstellungen inklusive Theme-Wechsel
über einen Neustart hinweg.

### P5 — Compose-UI auf Stores + echte Navigation (Android/Desktop) · Größe M · **abgeschlossen**

Weil iOS ab P7 nativ navigiert, betrifft die Navigations-Library nur noch Android und Desktop —
das senkt das Risiko der Bibliothekswahl deutlich.

* **Bibliothekswahl:** `androidx.navigation:navigation-compose` in der Multiplatform-Variante
  als Default (stabil, Compose-Multiplatform-tauglich). Navigation 3 nur, wenn zum Umsetzungszeitpunkt
  nicht mehr Alpha — sonst ist die Migration ein späteres, isoliertes Ticket. Voyager als Rückfallebene,
  falls die androidx-Variante auf Desktop Probleme macht.
* Typisierte Routen statt `enum AppScreen` + `when`; echter Back-Stack; State-Erhalt beim Tab-Wechsel;
  Deep Links für `dhbw://timetable?week=…` und `dhbw://grades/{semesterId}`.
* Screens werden zu reinen Funktionen von `State` → UI plus `dispatch(Intent)`. Keine Service-Parameter,
  keine `remember`-Konstruktion von Abhängigkeiten mehr. (P4 hat das für alle vier Seiten schon
  getan; offen bleibt nur, dass sie ihren Store noch selbst per `koinInject()` holen.)
* `DisposableEffect { onDispose { viewModel.cleanup() } }` in `GradesPage`/`DocumentsPage` entfällt.
  (In P4 bereits entfallen, weil es keine ViewModels mehr gibt.)
* Der `EnsureLoaded`/`Load`-Trick aus P4 wird überflüssig, sobald der Navigations-Scope existiert:
  dann entscheidet die Store-Lebensdauer über das Nachladen, nicht ein Intent.

**Fertig wenn:** Zurück-Geste auf Android verhält sich erwartbar; Wechsel Timetable → Grades → Timetable
löst keinen Netzwerk-Request aus; Deep Link öffnet den richtigen Screen bei kaltem Start.

**Was tatsächlich passiert ist** (`phase/p5-navigation`):

* **Bibliotheksentscheidung** (offener Punkt 1 aus §3): Navigation 3 steht bei `1.2.0-alpha07` und
  hat keine Multiplatform-Variante — die Regel des Plans greift also und es wurde
  `org.jetbrains.androidx.navigation:navigation-compose` **2.9.2** (stabil). Voyager wurde nicht
  gebraucht, die androidx-Variante läuft auf Desktop.
* Typisierte Routen in `ui/navigation/Routes.kt`. `AppScreen` ist damit ganz entfallen — auch aus
  `AppStore`: seit es einen echten Back-Stack gibt, ist der die **einzige** Antwort auf „wo bin
  ich", und ein zweiter Zustand daneben könnte ihm widersprechen. `AppState` hält nur noch die
  eine Entscheidung, die keine Navigation ist: Login-Screen oder App.
* Die Seiten haben ihren `isLoggedIn`-Parameter verloren. Vier Screens haben je die Hälfte
  derselben Frage beantwortet (Navigationsleiste verstecken, Logout-Knopf verstecken); jetzt
  rendert eine Seite überhaupt nur noch innerhalb des eingeloggten Graphen.
* `SettingsPage` bekam zehn Parameter durchgereicht, fünf davon Callbacks, die nur eine
  Preference zurückgeschrieben haben. Sie liest jetzt ihren eigenen `SettingsStore`.
* `useMaterialYou` wirkt jetzt auch auf Desktop: ohne die Option gibt es das statische Schema der
  App statt des Seed-Schemas. Die Einstellung war dort sichtbar und wirkungslos.
* Der Restore-Zustand ist sichtbar geworden: solange die gespeicherte Sitzung nicht geprüft ist,
  zeigt die Wurzel einen Ladeindikator statt zu raten.

**Ein Fallstrick fürs Testen:** `NavBackStackEntry` bewegt seine eigene `LifecycleRegistry`, und
die weigert sich außerhalb des Main-Threads. Ein Klick aus dem Compose-Test-Thread löst genau das
aus („State must be at least 'CREATED'" bzw. „must be called on the main thread"). Die
Navigationstests steuern den Controller deshalb über `runOnUiThread`.

**Gate bei Abschluss:** `testDebugUnitTest` 288 Tests, `desktopTest` 386 Tests, 0 Fehler,
0 übersprungen. Framework Compose-frei (0), iOS-Build und -Start grün. **Alle drei
Abnahmekriterien auf dem Gerät nachgemessen:** Zurück aus einem Tab führt zum Stundenplan, Zurück
vom Stundenplan verlässt die App; ein kompletter Tab-Durchlauf macht **0 Requests**;
`dhbw://grades` und `dhbw://timetable?week=-2` öffnen bei kaltem Start den richtigen Screen — die
Woche „03 - 07 Aug" statt „17 - 21 Aug".

**Bewusst nicht gemacht:** die Stores in den Navigations-Scope zu verschieben. Sie bleiben
Applikations-Singles. Ein Halter je Navigationseintrag würde bei jedem Tab-Wechsel neu laden —
genau das, was P4 abgestellt hat — und `saveState`/`restoreState` müssten das dann wieder
zurückholen, ohne dass der Nutzer etwas davon hätte. `EnsureLoaded` bleibt deshalb bestehen.

### P6 — Room-Migrationen statt Datenverlust · Größe M

* `fallbackToDestructiveMigration(dropAllTables = true)` in allen vier `getDatabaseBuilder()`-Actuals
  (Android, Desktop, iOS, macOS) entfernen.
* Schema v4 → v5 mit expliziter `Migration`; `MigrationTestHelper`-Tests gegen die exportierten
  JSONs in `schemas/`; `AutoMigration` wo möglich, manuell wo Spalten umbenannt werden.
* CI-Gate: Schema-Export-Diff ohne zugehörige Migration lässt den Build fehlschlagen.
* **iOS-Pfadwechsel:** DB liegt heute in `NSDocumentDirectory` — dort kann die Widget-Extension
  nicht lesen. Umzug in den App-Group-Container (`group.de.fampopprol.dhbwhorb`), mit
  einmaligem Copy-Migrationsschritt beim ersten Start von v3. Voraussetzung für P8.

**Fertig wenn:** ein Update von v2.1.1 auf v3 behält Noten und Stundenplan; Migrationstest läuft in CI.

### P7 — Natives SwiftUI-Interface · Größe XL

* Neues Xcode-Target-Layout: `iosApp` bindet `Shared.framework` statt `ComposeApp`.
  `ComposeView`/`MainViewController` und der iOS-Compose-Pfad entfallen am Ende der Phase.
* **Swift-Brücke** — ein generischer Wrapper, einmal geschrieben:
  ```swift
  @Observable final class StoreBox<S: AnyObject, I: AnyObject, E: AnyObject> {
      private(set) var state: S
      private let store: any Store
      func dispatch(_ intent: I) { store.dispatch(intent: intent) }
      // state/effects werden über SKIEs AsyncSequence-Bridging konsumiert
  }
  ```
  SKIE liefert `for await state in store.state` und mappt `Effect`/`AppError` auf Swift-Enums,
  sodass `switch` erschöpfend prüfbar ist.
* Screens in echten Apple-Komponenten:
  * `TabView` als Root, `NavigationStack` je Tab
  * Login: `Form` + `SecureField`, Keychain-Autofill via `.textContentType(.password)`
  * Stundenplan: `List` mit `.refreshable`, Wochenwechsel über `TabView(.page)` oder
    `ScrollView(.horizontal)` mit `.scrollTargetBehavior(.paging)`, Detail als `.sheet` mit
    `.presentationDetents`
  * Noten: `List` mit `Section` je Semester, `Picker` für Semesterauswahl, `.searchable`
  * Dokumente: `List` + `.searchable`, Download über `QuickLook`/`ShareLink` statt eigenem Viewer
  * Einstellungen: `Form` mit `Toggle`/`Picker`, Dark-Mode über `.preferredColorScheme`
  * Systemschrift, `.tint` statt Material-You-Seed, Dynamic Type und VoiceOver von Anfang an
* Der iOS-Teil von `material-kolor`/Material3 wird nicht portiert — auf iOS gilt HIG, nicht Material.

**Fertig wenn:** alle fünf Screens laufen ohne Compose auf dem Gerät; Framework-Größe gegenüber
der P1-Baseline messbar kleiner; VoiceOver-Durchlauf je Screen ohne unbeschriftete Elemente.

### P8 — iOS-Plattformdienste nativ · Größe L

* **BGTaskScheduler:** `LectureMonitorScheduler.ios.kt` ist heute ein reiner Log-Stub —
  die Doku im Code beschreibt die Swift-Implementierung, die es nicht gibt. Registrierung in
  `iOSApp.init()`, `BGAppRefreshTaskRequest` mit `de.fampopprol.dhbwhorb.lecture-monitor`,
  Info.plist-Eintrag, Task ruft `CheckLectureChangesUseCase` aus `:shared`.
* **UNUserNotificationCenter** in Swift: Berechtigung, Kategorien, Actions
  („Woche öffnen"), Zustellung. Kotlin liefert nur noch das Domain-Event `LectureChange`.
* **WidgetKit:** die Swift-Codable-Spiegel `WidgetClassInfo`/`WidgetDayInfo`/`UpNextInfo` in
  `TimetableWidget.swift` sind heute eine manuell gepflegte Kopie von `WidgetSerializableModels.kt` —
  jede Feldänderung bricht das Widget still. Ersatz: Widget-Extension linkt `Shared.framework`
  und ruft `WidgetSnapshotUseCase` direkt gegen die App-Group-DB aus P6. `WidgetDataWriter`,
  der JSON-Umweg über `NSUserDefaults` und die Notification-Brücke in `ContentView` entfallen.
* **Keychain:** `SecureStorage.ios.kt` bekommt eine Keychain-Access-Group, damit Widget und
  App dieselben Credentials sehen.

**Fertig wenn:** Widget aktualisiert ohne laufende App; Background-Refresh liefert im Gerätetest
eine Benachrichtigung; kein DTO existiert doppelt in Kotlin und Swift.

### P9 — Aufräumen · Größe S

* `services/notifications/IntegrationExample.kt` (213 Zeilen Beispielcode mit eigenem
  `CoroutineScope`) löschen.
* Material3-Alpha-Pinning neu bewerten — die Expressive-Begründung gilt nach P7 nur noch
  für Android/Desktop.
* `AGENTS.md` und `.planning/PROJECT.md` auf die neue Struktur ziehen.
* CI: eigener macOS-Job für `:shared`-Framework-Build + Xcode-Build + Swift-Tests.

---

## 3. Querschnitt

**Teststrategie nach Schicht**

| Schicht | Art | Läuft auf |
|---|---|---|
| Parser | Fixture-Contract-Tests (P0) | commonTest |
| Repository | Fake-Remote + In-Memory-Room | commonTest / androidUnitTest |
| Reducer | pure Funktionstests, kein `runTest` | commonTest |
| Effect-Handler | `runTest` + Fake-Repositories | commonTest |
| Compose-UI | bestehende UI-Tests, auf Stores umgestellt | desktopTest |
| SwiftUI | XCTest + Snapshot-Tests | macOS-CI-Job |

UI-Tests dürfen nicht gegen sichtbaren Text assertieren, solange dieser aus lokalisierten
String-Ressourcen kommt — sonst hängt das Ergebnis an der Locale des ausführenden Rechners
(siehe P-1). Stattdessen stabile Test-Tags, die aus Enums abgeleitet sind.

**Reihenfolge-Abhängigkeiten:** P-1 → P0 → P1 → P2 → P3 → P4 → {P5, P6} → P7 → P8 → P9.
P5 und P6 sind unabhängig voneinander und parallelisierbar. P7 setzt P4 **und** P6 voraus
(App-Group-DB), P8 setzt P7 voraus.

**Offene Punkte, die vor der jeweiligen Phase zu klären sind**

1. *P5:* Ist Navigation 3 zum Umsetzungszeitpunkt stabil? Falls nein → `navigation-compose` Multiplatform.
2. *P6:* Soll der iOS-DB-Umzug bestehende Daten kopieren oder einmalig neu synchronisieren?
   Kopieren ist sauberer, Neu-Sync ist deutlich weniger Code.
3. *P7:* Bleibt macOS bei Compose-Desktop, oder bekommt es später dasselbe SwiftUI-Interface?
   Der Plan geht von „bleibt Compose-Desktop" aus.
4. *P8:* App-Group und Keychain-Access-Group erfordern Anpassungen im Apple-Developer-Portal
   (Entitlements, Provisioning) — muss vor P8 bereitstehen.

**Was der Umbau nicht löst:** Dualis bleibt HTML-Scraping ohne stabilen Vertrag. P0 macht Brüche
sichtbar statt sie zu verhindern. Ein Portal-Redesign bricht die App weiterhin — die Fixtures
sorgen nur dafür, dass es die CI merkt und nicht der Nutzer.
