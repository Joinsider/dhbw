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

### P0 — Sicherheitsnetz für die Parser · Größe S

Die Regex-Parser sind die einzige Schicht ohne typisierten Vertrag und werden in jeder späteren
Phase angefasst. Vor dem ersten Verschieben absichern.

* `commonTest/resources/fixtures/dualis/` anlegen: echte HTML-Antworten für Login-Redirect-Kette,
  Wochen-Stundenplan (voll / leer / Feiertagswoche), Notenübersicht (mehrere Semester, laufende Prüfung),
  Dokumentenliste, Session-Expired-Seite.
* Contract-Tests für `AuthParser`, `TimetableParser`, `GradeParser`, `DocumentParser`, `HtmlParser`
  gegen diese Fixtures — je Fixture ein erwartetes Domain-Objekt.
* Kover-Baseline für `data/dualis/remote/parser` festhalten und in CI als Schwelle setzen.

**Fertig wenn:** jeder Parser hat mindestens einen Happy-Path- und einen Degradations-Test
(leeres HTML, abgelaufene Session, unerwartete Struktur), und keiner davon wirft ungefangen.

### P1 — Modulschnitt, ohne Verhaltensänderung · Größe L

Reines Verschieben. Keine Logik ändern, kein Bugfix nebenbei — sonst ist der Diff nicht reviewbar.

* Module gemäß §1.1 anlegen, `settings.gradle.kts` erweitern.
* Verschieben: `data/dualis/**` → `:data:dualis`; `data/storage/**` → `:data:local`;
  `data/dualis/models/**` + `data/helpers/**` → `:domain`; `util/Platform.kt` → `:core:common`.
* KSP/Room-Konfiguration wandert vollständig nach `:data:local` (inkl. `schemas/`-Verzeichnis
  und der `kspAndroid`/`kspIos*`/`kspMacos*`/`kspDesktop`-Einträge).
* `:shared` als Umbrella anlegen, Framework `baseName = "Shared"`, `isStatic = true`,
  `export(projects.domain)` + `export(projects.presentation)` + `export(projects.core.common)`.
  Der bestehende `ComposeApp`-Framework-Export bleibt zunächst parallel bestehen, damit iOS
  weiterläuft, während P7 noch nicht fertig ist.
* SKIE-Plugin (`co.touchlab.skie`) auf `:shared` aktivieren.

**Fertig wenn:** `./gradlew build` grün auf allen Targets; `:shared`-Framework enthält keine
Compose-Symbole (Prüfung: `nm -gU Shared.framework/Shared | grep -ci androidx.compose` ergibt 0);
Framework-Größe protokolliert als Baseline für P7.

**Risiko:** Room-KSP über Modulgrenzen bei nativen Targets ist erfahrungsgemäß die fehleranfälligste
Stelle. Wenn `:data:local` auf iosArm64 nicht durchgeht, zuerst nur Android+Desktop schneiden und
die nativen Targets in einem Folge-Commit nachziehen.

### P2 — Koin: ein Composition Root statt drei · Größe M

Das ist der Punkt mit dem größten Stabilitätsgewinn pro Zeile.

* `:data:di` mit `dataModule`, `:domain` mit `domainModule`, `:presentation` mit `presentationModule`.
* `platformModule` als `expect fun` — liefert `HttpClientEngine`, `RoomDatabase.Builder`,
  `SecureStorage`, `NotificationDispatcher`, `BackgroundScheduler`.
* `initKoin(platformModule)` in `:shared`, aufgerufen aus `MainActivity.onCreate`,
  `desktopMain/main.kt` und `iOSApp.init()`.
* **Ersatzlos entfernen:**
  * die Parameter `testAuthenticationService`, `testCredentialsProvider`, `testSecureStorage`,
    `database`, `sharedHttpClient`, `sessionManager`, `isInitialized`, `databaseErrorMessage`
    aus `App()` — Produktionscode läuft heute über `test`-Parameter
  * `getDataWithRetry` in `GradesViewModel` und `TimetableViewModel` (5×1s Polling)
  * der 500ms-`timeoutJob` in `MainActivity.onCreate`
  * `NotificationServiceLocator` (ersetzt durch Koin-Auflösung im Worker)
  * die Inline-Service-Konstruktion in `GradesPage` und `DocumentsPage`
* HttpClient-Konfiguration existiert genau einmal (heute weicht iOS ab: kein `HttpTimeout`).
* Tests injizieren über `KoinTestRule` mit überschriebenen Modulen statt über Konstruktorparameter.

**Fertig wenn:** Volltextsuche nach `test` in `App.kt` liefert nichts; kein Service-Typ ist
irgendwo nullable; `MainActivity` unter 120 Zeilen; App startet ohne künstliche Delays.

### P3 — Domain, Repositories, Fehlermodell · Größe L

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

### P4 — MVI-Stores · Größe L

Pro Feature ein Store in `:presentation`, jeweils `<Feature>State/Intent/Msg/Effect/Store`.

| Store | ersetzt | Besonderheit |
|---|---|---|
| `AuthStore` | `LoginFormViewModel` + Login-Zweige in `App.kt` | Demo-Mode als expliziter State |
| `TimetableStore` | `TimetableViewModel` | Wochen-Paging als `Map<Int, WeekState>`, Single-Flight je Woche |
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

### P5 — Compose-UI auf Stores + echte Navigation (Android/Desktop) · Größe M

Weil iOS ab P7 nativ navigiert, betrifft die Navigations-Library nur noch Android und Desktop —
das senkt das Risiko der Bibliothekswahl deutlich.

* **Bibliothekswahl:** `androidx.navigation:navigation-compose` in der Multiplatform-Variante
  als Default (stabil, Compose-Multiplatform-tauglich). Navigation 3 nur, wenn zum Umsetzungszeitpunkt
  nicht mehr Alpha — sonst ist die Migration ein späteres, isoliertes Ticket. Voyager als Rückfallebene,
  falls die androidx-Variante auf Desktop Probleme macht.
* Typisierte Routen statt `enum AppScreen` + `when`; echter Back-Stack; State-Erhalt beim Tab-Wechsel;
  Deep Links für `dhbw://timetable?week=…` und `dhbw://grades/{semesterId}`.
* Screens werden zu reinen Funktionen von `State` → UI plus `dispatch(Intent)`. Keine Service-Parameter,
  keine `remember`-Konstruktion von Abhängigkeiten mehr.
* `DisposableEffect { onDispose { viewModel.cleanup() } }` in `GradesPage`/`DocumentsPage` entfällt.

**Fertig wenn:** Zurück-Geste auf Android verhält sich erwartbar; Wechsel Timetable → Grades → Timetable
löst keinen Netzwerk-Request aus; Deep Link öffnet den richtigen Screen bei kaltem Start.

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
