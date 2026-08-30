/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.auth

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `handleUsernameFieldKeyEvent`/`handlePasswordFieldKeyEvent` are plain functions extracted out of
 * the `onKeyEvent` modifiers in [LoginForm], so they can be driven directly with a synthetic
 * [KeyEvent] instead of needing real hardware-keyboard input through the Compose UI test harness.
 * The `KeyEvent(...)` constructor used here is desktop/skiko-only, hence this lives in desktopTest
 * rather than commonTest. An unattached [FocusRequester] is safe to call `requestFocus()` on — it
 * just no-ops (logs a warning) instead of throwing.
 */
@OptIn(InternalComposeUiApi::class)
class LoginFormKeyEventTest {

    private fun keyEvent(key: Key, type: KeyEventType, isShiftPressed: Boolean = false) =
        KeyEvent(key = key, type = type, isShiftPressed = isShiftPressed)

    @Test
    fun usernameField_tabKeyDown_movesFocusAndConsumesEvent() {
        val passwordFocusRequester = FocusRequester()

        val consumed = handleUsernameFieldKeyEvent(
            keyEvent(Key.Tab, KeyEventType.KeyDown),
            passwordFocusRequester,
        )

        assertTrue(consumed)
    }

    @Test
    fun usernameField_otherKey_isIgnored() {
        val passwordFocusRequester = FocusRequester()

        val consumed = handleUsernameFieldKeyEvent(
            keyEvent(Key.A, KeyEventType.KeyDown),
            passwordFocusRequester,
        )

        assertFalse(consumed)
    }

    @Test
    fun usernameField_tabKeyUp_isIgnored() {
        val passwordFocusRequester = FocusRequester()

        val consumed = handleUsernameFieldKeyEvent(
            keyEvent(Key.Tab, KeyEventType.KeyUp),
            passwordFocusRequester,
        )

        assertFalse(consumed)
    }

    @Test
    fun passwordField_shiftTab_movesFocusBackAndConsumesEvent() {
        val usernameFocusRequester = FocusRequester()
        var loginTriggered = false

        val consumed = handlePasswordFieldKeyEvent(
            keyEvent(Key.Tab, KeyEventType.KeyDown, isShiftPressed = true),
            usernameFocusRequester,
        ) { loginTriggered = true }

        assertTrue(consumed)
        assertFalse(loginTriggered)
    }

    @Test
    fun passwordField_tabWithoutShift_consumesEventWithoutMovingFocus() {
        val usernameFocusRequester = FocusRequester()
        var loginTriggered = false

        val consumed = handlePasswordFieldKeyEvent(
            keyEvent(Key.Tab, KeyEventType.KeyDown, isShiftPressed = false),
            usernameFocusRequester,
        ) { loginTriggered = true }

        assertTrue(consumed)
        assertFalse(loginTriggered)
    }

    @Test
    fun passwordField_enterKeyDown_triggersLogin() {
        val usernameFocusRequester = FocusRequester()
        var loginTriggered = false

        val consumed = handlePasswordFieldKeyEvent(
            keyEvent(Key.Enter, KeyEventType.KeyDown),
            usernameFocusRequester,
        ) { loginTriggered = true }

        assertTrue(consumed)
        assertTrue(loginTriggered)
    }

    @Test
    fun passwordField_otherKey_isIgnored() {
        val usernameFocusRequester = FocusRequester()
        var loginTriggered = false

        val consumed = handlePasswordFieldKeyEvent(
            keyEvent(Key.A, KeyEventType.KeyDown),
            usernameFocusRequester,
        ) { loginTriggered = true }

        assertFalse(consumed)
        assertFalse(loginTriggered)
    }
}
