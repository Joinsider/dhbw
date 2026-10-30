# ADR-0002: Wählbare Akzentfarbe auf iOS und macOS

- **Status:** Angenommen
- **Datum:** 2026-10-29
- **Auslöser:** Support-Anfrage — „wird es in Zukunft die Möglichkeit geben, andere Farben (z. B.
  pink 🩷) zu verwenden?“
- **Betrifft:** `iosApp/` (Design, Settings), `:presentation` (SettingsState), `:data`
  (PreferencesRepository), Widget-Extension

## Kontext

### Der Stand je Plattform

| Plattform | Farbwahl heute |
|---|---|
| Android, Material You an | Farbe aus dem Hintergrundbild, kein Picker |
| Android, Material You aus | `ColorPicker` mit 19 Seed-Farben, **Pink ist dabei** (sogar zweimal: `0xFFFF91FF` und `0xFFD81B60`) |
| Desktop (JVM) | derselbe `ColorPicker`, Schema per MaterialKolor aus dem Seed |
| iOS / macOS | **keine Wahl** — fest DHBW-Rot (`Color.brand`) |

Der Wunsch ist also auf Android und Desktop schon erfüllt. Die Anfrage kommt sehr wahrscheinlich von
iOS (siehe ADR-0001: die hängende Badge gibt es nur dort), und dort gibt es die Möglichkeit nicht.

### Warum iOS bisher keine Wahl hat

`SettingsScreen.swift` blendet Material You und Seed-Farbe bewusst aus: Sie erzeugen eine
Material-Palette, und iOS hat weder Material noch Hintergrundbild-Farben. `Theme.swift` setzt
stattdessen ein Akzent-Rot über `.tint(.brand)` am Root — Schalter, Links, gewählter Tab und
Pull-to-Refresh folgen ihm.

Eine Farbe ist dort aber nicht nur der Tint. Aus dem Rot abgeleitet sind auch:

- `lectureFill` — die deckende Füllung der Vorlesungsblöcke, je hell/dunkel;
- die angehobene Dunkel-Variante von `brand`, weil das Icon-Rot auf Schwarz nicht lesbar ist;
- `examFill`/`examEdge` — Prüfungen sind *orange*, gerade **weil** der Akzent schon rot ist.

Ein beliebiger Farbwert reicht deshalb nicht. Jede Akzentfarbe braucht ein abgestimmtes Paar
(hell/dunkel) plus Blockfüllung, und sie darf nicht mit der Prüfungsfarbe verwechselbar sein.

### Persistenz

`SettingsState.seedColor` (ARGB-`Long`) existiert und wird über `PreferencesRepository` in
`PlatformSettings` gespeichert — auf Apple in `NSUserDefaults.standardUserDefaults`. Das Widget läuft
in einem eigenen Prozess und sieht `standardUserDefaults` der App **nicht**; es teilt mit der App nur
die Datenbank im App-Group-Container.

## Entscheidung

**iOS und macOS bekommen eine kuratierte Auswahl von Akzentfarben, darunter Pink. Kein freier
Farbwähler. DHBW-Rot bleibt der Standard.**

1. **Kuratierte Palette in `Theme.swift`.** Ein Swift-`enum AccentChoice: String, CaseIterable` mit
   17 Einträgen, einer pro Seed-Farbe des Android-`ColorPicker` (dessen Rot ist `brand`, die zwei
   Pinktöne dort sind hier einer):
   `brand` (DHBW-Rot, Standard), `pink`, `purple`, `violet`, `indigo`, `blue`, `lightBlue`, `cyan`,
   `teal`, `green`, `lightGreen`, `lime`, `yellow`, `amber`, `orange`, `deepOrange`, `blueGrey`.
   Die von Android übernommenen Einträge behalten den Farbton des Seeds, im hellen Modus
   abgedunkelt bis 4,5:1 gegen Weiß, im dunklen aufgehellt bis 7:1 gegen Schwarz. Ein helleres
   Pink (#FF9FEE) war kurz Teil der Palette und wurde wieder entfernt: im hellen Modus erreichte es
   nur 3:1 und wirkte zu grell.
   Jeder Eintrag liefert `accent` und `lectureFill` je hell/dunkel. Gelb, Bernstein, Orange und
   Dunkelorange setzen Prüfungen violett statt orange, aus demselben Grund, aus dem das Rot sie
   orange setzt. Jede Kombination wird vor dem Merge gegen WCAG AA (4,5:1 für Text auf
   `lectureFill`, 3:1 für Bedienelemente) geprüft, hell und dunkel.
2. **Ein eigener Wert, nicht `seedColor`.** `PreferencesRepository` bekommt
   `getAccentChoice(): String` / `setAccentChoice(String)`, `SettingsState` ein Feld `accentChoice`,
   `SettingsIntent.AccentChoiceChanged`. Der Wert ist ein Bezeichner (`"pink"`), keine Farbe:
   - ein Material-Seed und ein iOS-Akzent sind verschiedene Dinge — dasselbe ARGB ergibt in
     MaterialKolor ein ganzes Tonschema, auf iOS genau einen Tint;
   - ein Bezeichner überlebt eine spätere Feinabstimmung der Farbwerte, ein gespeichertes ARGB nicht;
   - ein unbekannter Bezeichner (Downgrade, entfernte Farbe) fällt auf `brand` zurück.
   Der Store hält den Wert für alle Plattformen, ausgewertet wird er nur in Swift — wie heute
   `seedColor` nur in Compose.
3. **Widget folgt mit.** Der Bezeichner wird zusätzlich in den App-Group-`UserDefaults`
   (`group.de.fampopprol.dhbwhorb`) gespiegelt, und `AppModel` lädt die Widget-Timelines bei einer
   Änderung neu — derselbe Weg wie bei `observeTimetableChanges`. Ohne das wäre die App pink und das
   Widget rot.
4. **UI.** Im Bereich *Darstellung* der Einstellungen eine Zeile „Akzentfarbe“ mit runden Swatches
   (iOS: horizontale Reihe; macOS: dieselbe Reihe im `Form`). Wechsel wirkt sofort über
   `.tint(model.accent.color)` am Root statt `.tint(.brand)`.
5. **App-Icon bleibt DHBW-Rot.** Alternative Icons sind nicht Teil dieser Entscheidung (siehe E).

## Betrachtete Alternativen

### A — Nichts ändern, auf die Systemfarbe verweisen

iOS hat keine systemweite Akzentfarbe für Apps von Drittanbietern; macOS hat eine, aber die App
überschreibt sie mit `.tint(.brand)`. **Verworfen** — die Antwort wäre schlicht „nein“.

### B — `seedColor` wiederverwenden

Denselben `ColorPicker`-Wert auf iOS als Tint nutzen. Kein neues Feld. **Verworfen**: Die 19
Android-Seeds sind für Tonschemata gewählt, nicht für Lesbarkeit als einzelner Tint (Gelb
`0xFFFDD835` als Linkfarbe auf Weiß liegt weit unter 3:1), es fehlen Dunkel-Varianten und
Blockfüllungen, und Orange/Amber kollidieren mit der Prüfungsfarbe.

### C — Freier `ColorPicker` (SwiftUI)

Maximale Freiheit. **Verworfen**: Kontrast nicht garantierbar, Dunkelmodus müsste algorithmisch
erzeugt werden, und die Prüfungsfarbe kann beliebig getroffen werden. Der Supportaufwand für
„ich sehe meine Schalter nicht mehr“ wäre höher als der Nutzen.

### D — Material-Palette auf iOS nachbauen

MaterialKolor-ähnliches Schema in Swift. Bricht mit der Entscheidung, auf Apple Standard-Controls
mit einem Akzent zu nutzen (Kommentar in `Theme.swift`). **Verworfen.**

### E — Alternative App-Icons (pinkes Icon)

`UIApplication.setAlternateIconName` mit zusätzlichen Icon-Sets. Wäre die naheliegende Erweiterung
genau dieser Anfrage. **Zurückgestellt**: eigene Assets pro Farbe und Darstellung (hell, dunkel,
getönt), System-Dialog bei jedem Wechsel, auf macOS nicht verfügbar. Kann auf derselben
`AccentChoice` aufsetzen und bekommt bei Bedarf eine eigene ADR.

## Konsequenzen

**Positiv**
- Der Wunsch wird auf der Plattform erfüllt, auf der er fehlt; Android/Desktop haben ihn schon.
- Lesbarkeit und Prüfungs-Unterscheidung bleiben garantiert, weil jede Farbe von Hand abgestimmt ist.
- Neue Farben sind ein Eintrag im Enum plus Kontrastprüfung.

**Negativ / Risiken**
- Zwei Farbkonzepte im Store (`seedColor` für Compose, `accentChoice` für Swift). Bewusst — sie
  bedeuten Verschiedenes —, aber im KDoc von `SettingsState` zu erklären.
- Eine neue Abhängigkeit des Widgets von App-Group-`UserDefaults`; fehlt das Entitlement, fällt es
  auf `brand` zurück, nicht aus.
- Jede Palette muss in beiden Darstellungen und auf beiden Plattformen angesehen werden.

**Nebenbefund Android**
- Der `ColorPicker` enthält Pink zweimal (`0xFFFF91FF` und `0xFFD81B60`), beide als „pink“ kommentiert.
  Einer davon sollte weg oder umbenannt werden.
- Mit Material You (Standard) ist der Picker unsichtbar. Wer Pink sucht, findet ihn nur, wenn er
  vorher Material You abschaltet. Ein Hinweistext unter dem Schalter wäre die kleinste Abhilfe.

**Tests**
- `SettingsStoreTest`: `AccentChoiceChanged` persistiert und landet im State; unbekannter Wert → `brand`.
- `reduceSettings` direkt für den neuen `Msg`.
- `KoinGraphTest` bleibt grün (keine neue Bindung, nur neue Methoden am Repository).
