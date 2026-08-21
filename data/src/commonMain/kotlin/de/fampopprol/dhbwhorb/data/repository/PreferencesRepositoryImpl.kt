/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemePreferences
import de.fampopprol.dhbwhorb.domain.repository.PreferencesRepository

/**
 * One entry point for the settings, over the two preference stores that hold them.
 *
 * The notification side goes through the interactor rather than the raw preferences so the
 * schedulers' state flows still see a change made here.
 */
class PreferencesRepositoryImpl(
    private val themePreferences: ThemePreferences,
    private val notificationPreferences: NotificationPreferencesInteractor
) : PreferencesRepository {

    override fun getThemeMode(): ThemeMode = themePreferences.getThemeMode()
    override fun setThemeMode(mode: ThemeMode) = themePreferences.setThemeMode(mode)

    override fun isMaterialYouEnabled(): Boolean = themePreferences.getMaterialYouEnabled()
    override fun setMaterialYouEnabled(enabled: Boolean) = themePreferences.setMaterialYouEnabled(enabled)

    override fun getCustomColor(): Long = themePreferences.getCustomColor()
    override fun setCustomColor(color: Long) = themePreferences.setCustomColor(color)

    override fun areNotificationsEnabled(): Boolean = notificationPreferences.getNotificationsEnabled()
    override fun setNotificationsEnabled(enabled: Boolean) = notificationPreferences.setNotificationsEnabled(enabled)

    override fun areLectureAlertsEnabled(): Boolean = notificationPreferences.getLectureAlertsEnabled()
    override fun setLectureAlertsEnabled(enabled: Boolean) = notificationPreferences.setLectureAlertsEnabled(enabled)
}
