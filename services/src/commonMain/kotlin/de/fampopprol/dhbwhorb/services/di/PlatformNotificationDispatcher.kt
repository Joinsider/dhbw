/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.di

import org.koin.core.qualifier.named

/**
 * The platform's own notification dispatcher, before the badge is wrapped around it. Only
 * `servicesModule` resolves it; everything else gets the unqualified, badging one.
 */
internal val PlatformNotificationDispatcher = named("platformNotificationDispatcher")
