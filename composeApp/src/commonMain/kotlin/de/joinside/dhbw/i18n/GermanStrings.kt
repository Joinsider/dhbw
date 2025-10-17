/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.i18n

/**
 * German string resources.
 */
object GermanStrings : Strings {
    // App general
    override val appName = "DHBW Horb"

    // Login screen
    override val loginWithDualisAccount = "Mit Ihrem Dualis-Konto anmelden"
    override val login = "Anmelden"
    override val username = "Benutzername"
    override val password = "Passwort"
    override val enterUsername = "Benutzername eingeben"
    override val enterPassword = "Passwort eingeben"
    override val usernameCannotBeEmpty = "Benutzername darf nicht leer sein"
    override val passwordCannotBeEmpty = "Passwort darf nicht leer sein"
    override val loginSuccessful = "Anmeldung erfolgreich"
    override val loginFailed = "Anmeldung fehlgeschlagen"

    // Common
    override val ok = "OK"
    override val cancel = "Abbrechen"
    override val yes = "Ja"
    override val no = "Nein"
    override val save = "Speichern"
    override val delete = "Löschen"
    override val edit = "Bearbeiten"
    override val back = "Zurück"
    override val next = "Weiter"
    override val finish = "Fertig"
    override val loading = "Lädt..."
    override val error = "Fehler"
    override val success = "Erfolg"
    override val retry = "Wiederholen"

    // Settings
    override val settings = "Einstellungen"
    override val language = "Sprache"
    override val darkMode = "Dunkelmodus"
    override val lightMode = "Hellmodus"
    override val systemDefault = "Systemstandard"

    // Error messages
    override val networkError = "Netzwerkfehler. Bitte überprüfen Sie Ihre Verbindung."
    override val unknownError = "Ein unbekannter Fehler ist aufgetreten."
    override val invalidCredentials = "Ungültiger Benutzername oder Passwort."
}

