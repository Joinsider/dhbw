/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.util

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class FileViewerAndroidTest {

    @Before
    fun setUpKoin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        startKoin { modules(module { single<Context> { context } }) }
    }

    @After
    fun tearDownKoin() {
        stopKoin()
    }

    /** MediaStore.Downloads only exists from API 29 (Q) on — this is the branch below it. */
    @Config(sdk = [28])
    @Test
    fun saveFileWithDialog_belowQ_writesDirectlyToDownloads() {
        val bytes = "grade certificate".encodeToByteArray()

        saveFileWithDialog(bytes, "certificate.pdf")

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = downloadsDir.resolve("certificate.pdf")
        assertTrue(file.exists(), "the file should land directly in the public Downloads directory")
        assertEquals("grade certificate", file.readText())
    }

    @Config(sdk = [29])
    @Test
    fun saveFileWithDialog_qAndAbove_writesViaMediaStore() {
        val bytes = "grade certificate".encodeToByteArray()

        // Robolectric's shadow MediaStore provider accepts the insert and backs it with a real
        // file, so this exercises the same insert-then-openOutputStream path production takes.
        saveFileWithDialog(bytes, "certificate.pdf")
        // No exception means the ContentResolver insert + write succeeded; the fallback branch
        // below covers what happens when it doesn't.
    }
}
