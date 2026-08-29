/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-lifetime coroutine scope for work that outlives any single screen —
 * background refreshes, schedulers, cache warming.
 *
 * SupervisorJob so a single failing child does not tear down the rest. Provided through DI so
 * tests can substitute a scheduler-controlled scope instead of a real dispatcher.
 */
fun appCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
