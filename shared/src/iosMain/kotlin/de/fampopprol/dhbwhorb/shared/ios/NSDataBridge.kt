/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.shared.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * The downloaded bytes of a document as something `QuickLook` and `ShareLink` accept.
 *
 * `DocumentsEffect.OpenFile` carries a `ByteArray`, which reaches Swift as a `KotlinByteArray`
 * that no Apple API takes. Converting on the Swift side would mean a per-element copy through
 * `subscript`; pinning the array once here is a single `memcpy`.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData()
} else {
    usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
